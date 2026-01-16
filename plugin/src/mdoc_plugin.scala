package io.github.quafadas.millSite

import mill.*
import mill.scalalib.*
import mill.scalajslib.*
import coursier.maven.MavenRepository
import mill.api.Result
import mill.util.Jvm

// import mill.scalalib.api.CompilationResult
// import de.tobiasroeser.mill.vcs.version.VcsVersion
import scala.util.Try
import mill.scalalib.publish.PomSettings
import mill.scalalib.publish.License
import mill.scalalib.publish.VersionControl
import os.SubPath
import ClasspathHelp.*
import mill.api.Task.Simple
import mill.api.BuildCtx
import java.net.URLClassLoader

trait MdocModule extends ScalaModule:

  val jsSiteModule: SiteJSModule =
    new SiteJSModule:
      override def scalaVersion: Simple[String] = MdocModule.this.scalaVersion
      override def scalaJSVersion: Simple[String] = Task(Versions.scalaJsVersion)

  /** Finds everything that is going to get published
    *
    * @return
    */
  def findAllTransitiveDeps: Set[JavaModule] =
    def loop(
        acc: Set[JavaModule],
        current: JavaModule
    ): Set[JavaModule] =
      val newAcc = acc + current
      val newDeps = current.moduleDeps
        .filter(_.isInstanceOf[PublishModule])
        .filterNot(newAcc.contains(_))
        .toSet
      if newDeps.isEmpty then newAcc
      else newDeps.foldLeft(newAcc)((acc, dep) => loop(acc, dep))
      end if
    end loop
    moduleDeps.foldLeft(Set[JavaModule]())((acc, dep) => loop(acc, dep))
  end findAllTransitiveDeps

  // override def docSources = Task {
  //   Task.traverse(findAllTransitiveDeps.toSeq)(_.docSources)().flatten
  // }

  override def compileClasspath = Task {
    Task.traverse(findAllTransitiveDeps.toSeq)(_.compileClasspath)().flatten
      ++ super.compileClasspath()

  }

  def scalaMdocVersion: T[String] = Task(Versions.mdocVersion)

  // def scalaMetaDeps = Task {
  //   Seq(
  //     mvn"org.scalameta:common_2.13:4.13.9",
  //     mvn"org.scalameta::scalameta:4.13.9"
  //   )
  // }

  override def mvnDeps: T[Seq[Dep]] = super.mvnDeps() ++
    Seq(
      mvn"org.scalameta:common_2.13::${Versions.scalaMetaVersion}"
    )

  def siteVariables: Task.Simple[Seq[(String, String)]] = Task {
    Seq.empty[(String, String)]
  }

  def mdocDep: T[Seq[Dep]] = Task(
    Seq(
      mvn"org.scalameta::mdoc-js:${scalaMdocVersion()}",
      mvn"org.scalameta::mdoc:${scalaMdocVersion()}"
        .exclude("org.scala-lang" -> "scala3-compiler_3")
        .exclude("org.scala-lang" -> "scala3-library_3"),
      mvn"org.scala-lang::scala3-compiler:${scalaVersion()}",
      mvn"org.scala-lang::scala3-library:${scalaVersion()}",
      mvn"org.scala-lang::tasty-core:${scalaVersion()}",
      mvn"org.scala-lang.modules::scala-xml:2.1.0"
    )
  )

  def mDocLibs = Task {
    defaultResolver().classpath(mdocDep())
  }

  private def mdocClassPath = Task {
    val jsPropFile =
      if jsSiteModule.moduleDeps.isEmpty then Seq.empty[os.Path]
      else Seq(jsSiteModule.mdocJsProperties().path)

    mDocLibs().map(_.path) ++ jsPropFile
  }

  /** Directory containing the mdoc documentation sources for this module.
    *
    * Defaults to moduleDir/docs and serves as the input root for mdoc processing (e.g., Markdown files, configuration,
    * and related assets). Override to customize where your documentation lives within the module.
    *
    * @return
    *   Path to the docs directory under this module’s directory.
    */
  def mdocDir = super.moduleDir / "docs"

  /** Returns the directory that contains the project's mdoc documentation sources.
    *
    * The directory is exposed as a Mill source input so that changes to any files within it are tracked and invalidate
    * downstream tasks. The returned PathRef points to the directory and enables incremental rebuilds when its contents
    * change.
    *
    * @return
    *   a PathRef to the documentation source directory, tracked as a task input
    */
  def docDir: Simple[PathRef] = Task.Source(mdocDir)

  // def mdocFiles: Task[Seq[PathRef]] = Task {
  //   println("mdoc files")
  //   os.walk(docDir().path)
  //   .filter(_.toString().endsWith("mdoc.md")).map(PathRef(_))
  // }

  // def mdFiles: Task[Seq[PathRef]] = Task {
  //   println("mdFiles")
  //   os.walk(docDir().path).filter(f => !f.toString().endsWith("mdoc.md") && f.toString().endsWith(".md")).map(PathRef(_))
  // }

  // def scalametaCommon = Task {
  //   Seq(
  //     mvn"org.scalameta:common_2.13:4.13.9",
  //     mvn"org.scalameta::scalameta:4.13.9"
  //   )
  // }

  // def scalaMetaCommonLib = Task {
  //   defaultResolver().classpath(scalametaCommon())
  // }

  /** Configures mdocs arguments. See;
    *
    * https://scalameta.org/mdoc/docs/installation.html#help
    */
  def mdocArgs: Task[Seq[String]] = Task {
    val cp = compileClasspath().map(_.path)
    val runCp = runClasspath().map(_.path)
    // val scalametaCommon = scalaMetaCommonLib().map(_.path)
    val siteVars = siteVariables().toSeq.flatMap { case (k, v) => Seq(s"--site.$k", v) }

    val jsArgs = jsSiteModule.moduleDeps.isEmpty match
      case true  => Seq.empty[String]
      case false => Seq("--js-classpath", jsSiteModule.jsclasspath())

    // val toProcess = mdocFiles()
    val importMap =
      if jsSiteModule.scalaJSImportMap().isEmpty then None
      else
        Some(
          jsSiteModule.mdocJsImportMap().path.toIO.getAbsolutePath
        )
    val scalaCOpts = scalacOptions()
    Seq(
      "--in",
      docDir().path.toString()
    )
      ++ Seq("--classpath", toArgument(runCp ++ cp))
      ++ importMap.fold(Seq.empty[String])(i => Seq("--import-map-path", i))
      ++ (if scalaCOpts.nonEmpty then Seq("--scalac-options", scalaCOpts.mkString(" ")) else Seq.empty[String])
      ++ siteVars
    // ++ jsArgs
  }

  private def mdocWorker = Task.Worker {
    new MdocWorker(mdocClassPath(), forkArgs())
  }

  /** Runs mdoc to generate processed documentation into this task's destination directory.
    *
    * Behavior:
    *   - Ensures sources are compiled and documentation inputs are up to date by invoking `compile()` and `docDir()`.
    *   - Builds CLI arguments from `mdocArgs()`, appending `--out` pointing to `Task.dest`.
    *   - Resolves the mdoc runtime classpath from `mDocLibs()` and invokes `mdoc.Main` in a forked JVM with the
    *     configured JVM arguments (`forkArgs()`) and environment (`forkEnv()`).
    *
    * The resulting documentation is written under `Task.dest` and returned as a `PathRef`.
    *
    * Side effects:
    *   - Spawns a new JVM process to run mdoc.
    *   - Writes/overwrites files under this task’s destination directory.
    *
    * Failure conditions:
    *   - Fails if the mdoc process exits non‑zero or if required dependencies are unavailable.
    *
    * @return
    *   a `PathRef` pointing to the directory containing the generated documentation.
    */

  def mdoc2: Task.Simple[PathRef] = Task {
    compile()
    docDir() // force dependency tracking

    val args = mdocArgs() ++ Seq("--out", Task.dest.toString())
    mdocWorker().run(args)
    PathRef(Task.dest)
  }

  private final class MdocWorker(classpath: Seq[os.Path], forkArgs: Seq[String]) extends AutoCloseable:
    private var loader: URLClassLoader = null
    private def parseSystemProps: Seq[(String, String)] =
      forkArgs.collect {
        case arg if arg.startsWith("-D") =>
          val kv = arg.drop(2)
          kv.indexOf('=') match
            case -1  => kv -> ""
            case idx => kv.take(idx) -> kv.drop(idx + 1)
          end match
      }

    override def close(): Unit = Option(loader).foreach(_.close())

    private def withSystemProps[T](body: => T): T =
      val props = parseSystemProps
      val originals = props.map { case (k, _) => k -> Option(System.getProperty(k)) }
      props.foreach { case (k, v) => System.setProperty(k, v) }
      try body
      finally
        originals.foreach {
          case (k, Some(v)) => System.setProperty(k, v)
          case (k, None)    => System.clearProperty(k)
        }
      end try
    end withSystemProps

    def run(args: Seq[String]): Unit = this.synchronized {
      loader = Jvm.createClassLoader(classpath)
      try
        withSystemProps {
          mill.api.ClassLoader.withContextClassLoader(loader) {
            val mainObj = loader.loadClass("mdoc.Main$").getField("MODULE$").get(null)
            val process = mainObj.getClass.getMethod(
              "process",
              classOf[Array[String]],
              classOf[java.io.PrintStream],
              classOf[java.nio.file.Path]
            )
            val exitCode =
              try
                process
                  .invoke(
                    mainObj,
                    args.toArray,
                    /* silence normal mdoc stdout to keep tests clean */
                    new java.io.PrintStream(java.io.OutputStream.nullOutputStream()),
                    java.nio.file.Path.of(os.pwd.toString())
                  )
                  .asInstanceOf[Int]
              catch
                case ite: java.lang.reflect.InvocationTargetException =>
                  val cause = Option(ite.getCause).getOrElse(ite)
                  throw new RuntimeException(
                    s"mdoc invocation failed: ${cause.getClass.getSimpleName}: ${cause.getMessage}",
                    cause
                  )

            if exitCode != 0 then throw new RuntimeException(s"mdoc failed with exit code $exitCode")
            end if
          }
        }
      finally
        Option(loader).foreach(_.close())
      end try
    }
  end MdocWorker
end MdocModule
