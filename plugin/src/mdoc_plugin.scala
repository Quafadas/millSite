
package io.github.quafadas.millSite

import mill.*
import mill.scalalib.*
import mill.scalajslib.*
import coursier.maven.MavenRepository
import mill.api.Result
import mill.util.Jvm.createJar

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

trait MdocModule extends ScalaModule:

  val jsSiteModule: SiteJSModule =
    new SiteJSModule:
      override def scalaVersion = Task("3.7.2")
      override def scalaJSVersion = Task("1.19.0")

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
    Task.traverse(findAllTransitiveDeps.toSeq)(_.compileClasspath)().flatten ++ super.compileClasspath()
  }

  def scalaMdocVersion: T[String] = Task(Versions.mdocVersion)

  // override def mvnDeps: T[Seq[Dep]] = super.mvnDeps() ++
  //   Seq(
  //     mvn"org.scalameta::mdoc-js::${scalaMdocVersion()}",
  //     mvn"org.scalameta::mdoc::${scalaMdocVersion()}"
  //     .exclude("org.scala-lang" -> "scala3-compiler_3")
  //     .exclude("org.scala-lang" -> "scala3-library_3")
  //   )


  // def mdocDep: T[Seq[Dep]] = Task(
  //   Seq(
  //     mvn"org.scalameta::mdoc-js:${scalaMdocVersion()}",
  //     mvn"org.scalameta::mdoc:${scalaMdocVersion()}"
  //       .exclude("org.scala-lang" -> "scala3-compiler_3")
  //       .exclude("org.scala-lang" -> "scala3-library_3"),
  //     mvn"org.scala-lang::scala3-compiler:${scalaVersion()}",
  //     mvn"org.scala-lang::scala3-library:${scalaVersion()}",
  //     mvn"org.scala-lang::tasty-core:${scalaVersion()}",
  //     mvn"org.scala-lang.modules::scala-xml:2.1.0"
  //   )
  // )

  // def mDocLibs = Task {
  //   defaultResolver().classpath(mdocDep())
  // }

  def mdocDir = super.moduleDir / "docs"

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

  def pathToImportMap: T[Option[PathRef]] = None

  // def mdocT = Task {
  //   val mdoccd = mdocOnly()
  //   val others = mdFiles()

  //   os.copy(mdoccd.path, Task.dest, mergeFolders = true)
  //   others.foreach {
  //     f => os.copy(f.path, Task.dest / f.path.subRelativeTo(docDir().path), mergeFolders = true)
  //   }
  //   PathRef(Task.dest)
  // }

  def mdocT: Task[PathRef] = Task.Worker{
    mdoc.SbtMain.main(
      args = mdocArgs().toArray ++ Seq("--out", Task.dest.toString())
    )

    PathRef(Task.dest)
  }

  def mdocArgs: T[Seq[String]] = Task{
    val cp = compileClasspath().map(_.path)
    val runCp = runClasspath().map(_.path)

    // val toProcess = mdocFiles()
    val importMap = pathToImportMap().map(_.path.toIO.getAbsolutePath)
    val scalaCOpts = scalacOptions()
    Seq(
      "--in",
      docDir().path.toString(),
      // "--out",
      // Task.dest.toString()
    )
    ++ Seq("--classpath", toArgument(runCp ++ cp))
    ++ importMap.fold(Seq.empty[String])(i => Seq("--import-map-path", i))
    ++ (if scalaCOpts.nonEmpty then Seq("--scalac-options", scalaCOpts.mkString(" ")) else Seq.empty[String])
    // ++ Seq("--js-classpath", jsSiteModule.jsclasspath() )
  }

  /**
   * Generates the mdoc documentation for the module.
   *
   * TODO:
    - Mdoc JS
    - Caching
   */
//   def mdocOnly: Task[PathRef] = Task.Worker {

//     compile()
//     // val cacheDir = Task.dest / "cache"
//     // val mdoccdDir = Task.dest / "mdoccd"
//     // val cacheFile = cacheDir / "cache.json"
//     // if !os.exists(cacheDir) then os.makeDir.all(cacheDir)
//     // end if
//     // if !os.exists(mdoccdDir) then os.makeDir.all(mdoccdDir)
//     // end if
//     // if !os.exists(cacheFile) then os.write(cacheFile, "[]")
//     // end if

//     val mdocLibs_ = mDocLibs().map(_.path)
//     val cp = compileClasspath().map(_.path)
//     val runCp = runClasspath().map(_.path)
//     // val deps = mvnDeps()
//     // val deps2 = defaultResolver().classpath(deps).map(_.path)
//     val toProcess = mdocFiles()
//     // val cached = upickle.default.read[Seq[PathRef]](os.read(cacheFile))

//     // val cachedList =
//     //   cached.map(cmdoc => cmdoc.path.subRelativeTo(mdocDir)).toSet
//     // val currList =
//     //   mdocSources_.map(cmdoc => cmdoc.path.subRelativeTo(mdocDir)).toSet

//     // cachedList.diff(currList).foreach(del => os.remove(mdoccdDir / del))

//     val result = if !toProcess.isEmpty then

//       // val checkCache = toProcess.map(_.sig).diff(cached.map(_.sig))
//       val importMap = pathToImportMap().map(_.path.toIO.getAbsolutePath)
//       val scalaCOpts = scalacOptions()
//       val dirParams = toProcess
//           .map(_.path)
//           .map { pr =>
//             Seq(
//               "--in",
//               pr.toIO.getAbsolutePath,
//               "--out",
//               // Task.dest.toIO.getAbsolutePath,
//               (Task.dest / pr.subRelativeTo(mdocDir)).toIO.getAbsolutePath
//             )
//           }
//           .iterator
//           .flatten
//           .toSeq
//         ++ Seq("--classpath", toArgument(runCp ++ cp))
//         ++ importMap.fold(Seq.empty[String])(i => Seq("--import-map-path", i))
//         ++ (if scalaCOpts.nonEmpty then Seq("--scalac-options", scalaCOpts.mkString(" ")) else Seq.empty[String])
//         // ++ Seq("--js-classpath", jsSiteModule.jsclasspath() )


//       // println("running mdoc")
//       // println(dirParams.mkString("\n"))
//       // println("FORK ARGS")
//       // println(forkArgs().mkString("\n"))
//       // println("FORK ENV")
//       // println(forkEnv().mkString("\n"))
//       // println("FORK WORKING DIR")
//       // println(forkWorkingDir())
//       Result.create(
//           mill.util.Jvm.callProcess(
//             mainClass = "mdoc.Main",
//             classPath = mdocLibs_,// ++ Seq(jsSiteModule.mdocJsProperties().path),
//             jvmArgs = forkArgs(),
//             env = forkEnv(),
//             mainArgs = dirParams,
//             // cpPassingJarPath =
//             //   Some(Task.dest) // classpath can be long. On windows will barf without passing as Jar
//           )
//         )

//       else Result.Success("No mdoc sources found")

//     // if (os.exists(mdoccdDir / "_docs" / "_assets")) {
//     //   os.remove.all(mdoccdDir / "_assets")
//     //   os.move(
//     //     mdoccdDir / "_docs" / "_assets",
//     //     mdoccdDir / "_assets",
//     //     replaceExisting = true,
//     //     createFolders = true
//     //   )
//     // }
//     result match
//       case Result.Success(_) =>
//         // os.write.over(cacheFile, upickle.default.write(cached ++ toProcess))
//         Result.Success(PathRef(Task.dest))
//       case Result.Failure(msg) =>
//         Result.Failure(
//           s"mdoc failed with message: $msg\n" +
//             s"Please check the mdoc sources in ${docDir().path} and ensure they are valid."
//         )
//     end match
//   }
// end MdocModule
