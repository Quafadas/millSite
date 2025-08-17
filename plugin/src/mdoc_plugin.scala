
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

trait MdocModule extends ScalaModule:

  val jsSiteModule: SiteJSModule =
    new SiteJSModule:
      override def scalaVersion = Task("3.3.5")
      override def scalaJSVersion = Task("1.19.0")

  /** Finds everything that is going to get published
    *
    * @return
    */
  // def findAllTransitiveDeps: Set[JavaModule] =
  //   def loop(
  //       acc: Set[JavaModule],
  //       current: JavaModule
  //   ): Set[JavaModule] =
  //     val newAcc = acc + current
  //     val newDeps = current.moduleDeps
  //       .filter(_.isInstanceOf[PublishModule])
  //       .filterNot(newAcc.contains(_))
  //       .toSet
  //     if newDeps.isEmpty then newAcc
  //     else newDeps.foldLeft(newAcc)((acc, dep) => loop(acc, dep))
  //     end if
  //   end loop
  //   moduleDeps.foldLeft(Set[JavaModule]())((acc, dep) => loop(acc, dep))
  // end findAllTransitiveDeps

  // override def docSources = Task {
  //   Task.traverse(findAllTransitiveDeps.toSeq)(_.docSources)().flatten
  // }

  // override def compileClasspath = Task {
  //   Task.traverse(findAllTransitiveDeps.toSeq)(_.compileClasspath)().flatten ++ super.compileClasspath()
  // }  

  def scalaMdocVersion: T[String] = Task(Versions.mdocVersion)

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

  def mdocSourceDir: Simple[PathRef] = Task.Source(mdocDir)

  def mdocDir = super.moduleDir / "docs"

  def compileCpArg: T[Seq[String]] = Task {
    Seq(
      "-classpath",
      compileClasspath().iterator
        .filter(_.path.ext != "pom")
        .map(_.path)
        .mkString(java.io.File.pathSeparator)
    )
  }


  def scalaLibrary: T[Dep] = Task(
    mvn"org.scala-lang:scala-library:${scalaVersion}"
  )

  def mdocSources: T[Seq[PathRef]] = Task {
    os.walk(mdocDir)
      .filter(os.isFile)
      .filter(_.toIO.getName().contains("mdoc.md"))
      .map(PathRef(_))
  }

  def pathToImportMap: T[Option[PathRef]] = None

  def mdoc: Task[PathRef] = Task(persistent = true) {
    compile()
    val cacheDir = Task.dest / "cache"
    val mdoccdDir = Task.dest / "mdoccd"
    val cacheFile = cacheDir / "cache.json"
    if !os.exists(cacheDir) then os.makeDir.all(cacheDir)
    end if
    if !os.exists(mdoccdDir) then os.makeDir.all(mdoccdDir)
    end if
    if !os.exists(cacheFile) then os.write(cacheFile, "[]")
    end if

    val cp = runClasspath().map(_.path)
    // val deps = mvnDeps()
    // val deps2 = defaultResolver().classpath(deps).map(_.path)
    val dir = Task.dest.toIO.getAbsolutePath
    val mdocSources_ = mdocSources().filter(pr => os.isFile(pr.path))
    val cached = upickle.default.read[Seq[PathRef]](os.read(cacheFile))

    val cachedList =
      cached.map(cmdoc => cmdoc.path.subRelativeTo(mdocDir)).toSet
    val currList =
      mdocSources_.map(cmdoc => cmdoc.path.subRelativeTo(mdocDir)).toSet

    cachedList.diff(currList).foreach(del => os.remove(mdoccdDir / del))

    val result = if !mdocSources_.isEmpty then

      val checkCache = mdocSources_.map(_.sig).diff(cached.map(_.sig))
      val importMap = pathToImportMap().map(_.path.toIO.getAbsolutePath)
      println(checkCache)
      val toProceass = mdocSources_.filter { pr =>
        checkCache.contains(pr.sig)
      }
      val res = if !toProceass.isEmpty then

        val dirParams = toProceass
          .map(_.path)
          .map { pr =>
            Seq(
              "--in",
              pr.toIO.getAbsolutePath,
              "--out",
              (mdoccdDir / pr.subRelativeTo(mdocDir)).toIO.getAbsolutePath,
              "--scalac-options",
              scalacOptions().mkString(" ")
            )
          }
          .iterator
          .flatten
          .toSeq ++ Seq("--classpath", toArgument(cp /* ++ deps2*/)) ++ importMap.fold(
          Seq.empty[String]
        )(i => Seq("--import-map-path", i))
        Seq("--js-classpath", jsSiteModule.jsclasspath() )

        val arg1 = Result.create(
          mill.util.Jvm.callProcess(
            mainClass = "mdoc.Main",
            classPath = /*rp ++ */ Seq(jsSiteModule.mdocJsProperties().path),
            jvmArgs = forkArgs(),
            env = forkEnv(),
            mainArgs = dirParams,
            cpPassingJarPath =
              Some(forkWorkingDir()) // classpath can be long. On windows will barf without passing as Jar
          )
        )
        os.write.over(cacheFile, upickle.default.write(mdocSources_))
        arg1
      else Result.Success("No mdoc sources found")

    if (os.exists(mdoccdDir / "_docs" / "_assets")) {
      os.remove.all(mdoccdDir / "_assets")
      os.move(
        mdoccdDir / "_docs" / "_assets",
        mdoccdDir / "_assets",
        replaceExisting = true,
        createFolders = true
      )
    }

    PathRef(mdoccdDir)
  }
end MdocModule
