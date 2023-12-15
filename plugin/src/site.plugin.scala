package mill.site

import mill._
import mill.scalalib._
import mill.api.Result
import mill.util.Jvm.createJar
import mill.api.PathRef
import mill.scalalib.api.CompilationResult

trait SiteModule extends ScalaModule {

  def scalaVersion = T("3.3.1")

  def transitiveDocs: T[Boolean] = T { true }

  def scalaMdocVersion: T[String] = T("2.5.1")

  def mdocDep: T[Agg[Dep]] = T(
    Agg(
      ivy"org.scalameta::mdoc:${scalaMdocVersion()}"
        .exclude("org.scala-lang" -> "scala3-compiler_3")
        .exclude("org.scala-lang" -> "scala3-library_3"),
      ivy"org.scala-lang::scala3-compiler:${scalaVersion()}"
    )
  )

  def mdocDepBound: T[Agg[BoundDep]] =
    mdocDep().map(Lib.depToBoundDep(_, scalaVersion()))

  def mDocLibs = T { resolveDeps(mdocDepBound) }

  def transitiveDocSources: T[Seq[PathRef]] =
    T { T.traverse(moduleDeps)(_.docSources)().flatten }

  def compileCpArg: T[Seq[String]] = T {
    Seq(
      "-classpath",
      compileClasspath().iterator
        .filter(_.path.ext != "pom")
        .map(_.path)
        .mkString(java.io.File.pathSeparator)
    )
  }
  override def scalaDocOptions =
    super.scalaDocOptions() ++ Seq[String]("-snippet-compiler:compile")

  def siteGen: T[os.Path] = T {
    val apidir = apiOnlyGen()
    val docdir = docOnlyGen()
    // mdoc()
    os.copy.over(apidir, T.dest)
    os.copy.over(docdir / "docs", T.dest / "docs")
    T.dest
  }

  def docOnlyGen: T[os.Path] = T {
    compile()
    mdoc()
    val javadocDir = T.dest / "javadoc"
    os.makeDir.all(javadocDir)
    val combinedStaticDir = T.dest / "static"
    os.makeDir.all(combinedStaticDir)

    //copy mdoccd files in
    for {
      aDoc <- os.walk(mdoc().path)
      rel = (combinedStaticDir / aDoc.subRelativeTo(mdoc().path))
    } {
      os.copy.over(aDoc, rel)
    }

    //copy all other doc files
    for {
      aDoc <- os.walk(mdocSourceDir().path)
      rel = (combinedStaticDir / aDoc.subRelativeTo(mdocDir));
      if !os.exists(rel)
    } {
      os.copy(aDoc, rel)
    }

    val compileCp = compileCpArg
    val options = Seq(
      "-d",
      javadocDir.toNIO.toString,
      "-siteroot",
      combinedStaticDir.toNIO.toString
    )
    zincWorker()
      .worker()
      .docJar(
        scalaVersion(),
        scalaOrganization(),
        scalaDocClasspath(),
        scalacPluginClasspath(),
        options ++ compileCpArg() ++ scalaDocOptions()
          ++ Lib
            .findSourceFiles(Seq(fakeSource().classes), Seq("tasty"))
            .map(_.toString()) // find classes in this artefact _only_!

      ) match {
      case true => Result.Success(javadocDir)
      case false =>
        Result.Failure(
          s"Documentation generatation failed. Usual cause would be no sources files in : ${sources()} or no doc files in ${docSources()} "
        )
    }
  }

  def fakeDoc: T[PathRef] = T {
    val emptyDoc = T.dest / "_docs" / "empty.md"
    os.makeDir(emptyDoc / os.up)
    os.write.over(
      emptyDoc,
      "# Fake Doc \n \n To trick the API generator into having a link to the docs part of the website"
        .getBytes()
    )
    PathRef(T.dest)
  }

  def fakeSource: T[CompilationResult] = T {
    val emptyDoc = T.dest / "src" / "fake.scala"
    os.makeDir(emptyDoc / os.up)
    os.write.over(
      emptyDoc,
      "package fake \n \n object Fake: \n  def apply() = ???"
    )
    zincWorker()
      .worker()
      .compileMixed(
        upstreamCompileOutput = upstreamCompileOutput(),
        sources = Seq(emptyDoc),
        compileClasspath = compileClasspath().map(_.path),
        javacOptions = javacOptions(),
        scalaVersion = scalaVersion(),
        scalaOrganization = scalaOrganization(),
        scalacOptions = allScalacOptions(),
        compilerClasspath = scalaCompilerClasspath(),
        scalacPluginClasspath = scalacPluginClasspath(),
        reporter = T.reporter.apply(hashCode),
        reportCachedProblems = zincReportCachedProblems()
      )
  }

  def apiOnlyGen: T[os.Path] = T {
    compile()
    val javadocDir = T.dest / "javadoc"
    os.makeDir.all(javadocDir)
    val combinedStaticDir = T.dest / "static"
    os.makeDir.all(combinedStaticDir)

    val compileCp = compileCpArg
    val options = Seq(
      "-d",
      javadocDir.toNIO.toString,
      "-siteroot",
      fakeDoc().path.toNIO.toString
    )

    zincWorker()
      .worker()
      .docJar(
        scalaVersion(),
        scalaOrganization(),
        scalaDocClasspath(),
        scalacPluginClasspath(),
        options ++ compileCpArg() ++ scalaDocOptions()
          ++ Lib
            .findSourceFiles(transitiveDocSources(), Seq("tasty"))
            .map(_.toString()) // transitive api, i.e. module deps.

      ) match {
      case true => Result.Success(javadocDir)
      case false =>
        Result.Failure(
          s"Documentation generatation failed. This would normally indicate that the standard mill `docJar` command on one of the underlying projects will fail. Please attempt to fix that problem and try again  "
        )
    }
  }

  def scalaLibrary: T[Dep] = T(
    ivy"org.scala-lang:scala-library:${scalaVersion}"
  )

  def sitePath: T[os.Path] = T { siteGen() / "javadoc" }

  def sitePathString: T[String] = T { sitePath().toString() }

  /** Overwrites any md files which have been processed by mdoc.
    */

  override def docResources = T {
    val out = super.docResources()
    os.copy.over(mdoc().path, T.dest)

    Seq(PathRef(T.dest))
  }

  def npmInstallServeDeps() = T.command {
    println("npm install -g browser-sync")
    os.proc("npm", "install", "-g", "browser-sync").call(stdout = os.Inherit)
  }

  def serveLocal() = T.command {
    println("browser-sync start --server --ss " + sitePathString() + " -w")
    os.proc("browser-sync", "start", "--server", "--ss", sitePathString(), "-w")
      .call(stdout = os.Inherit)
  }

  def guessGithubAction: T[String] =
    s""""
  buildSite:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
        with:
          fetch-depth: 0

      - uses: actions/setup-java@v1
        with:
          java-version: 17
      - uses: actions/checkout@v3
      - run: ./millw ${artifactName()}.docJar
      - uses: actions/upload-artifact@master
        with:
          name: page
          path: ${sitePathString()}

  deploySite:
    runs-on: ubuntu-latest
    needs: buildSite
    environment:
      name: github-pages
      url: $${{steps.deployment.outputs.page_url}}
    steps:
      - uses: actions/download-artifact@master
        with:
          name: page
          path: .
      - uses: actions/configure-pages@v1
      - uses: actions/upload-pages-artifact@v1
        with:
          path: .
      - id: deployment
        uses: actions/deploy-pages@main
"""

  val separator: Char = java.io.File.pathSeparatorChar
  def toArgument(p: Agg[os.Path]): String = p.iterator.mkString(s"$separator")

  // This is the source directory, of the entire site.
  def siteSources: T[Seq[PathRef]] = T.sources { super.millSourcePath / "docs" }

  def mdocSources: T[Seq[PathRef]] = T.sources {
    os.walk(mdocDir)
      .filter(os.isFile)
      .filter(_.toIO.getName().contains("mdoc.md"))
      .map(PathRef(_))
  }

  def mdocSourceDir = T.source { mdocDir }

  def mdocDir = super.millSourcePath / "docs"



  def mdoc: T[PathRef] = T {
    val cp = compileClasspath().map(_.path)
    val rp = mDocLibs().map(_.path)
    val dir = T.dest.toIO.getAbsolutePath
    val dirParams = mdocSources()
      .map(_.path)
      .map { pr =>
        Seq(
          "--in",
          pr.toIO.getAbsolutePath,
          "--out",
          (T.dest / pr.subRelativeTo(mdocDir)).toIO.getAbsolutePath
        )
      }
      .iterator
      .flatten
      .toSeq ++ Seq("--classpath", toArgument(cp ++ rp))

    mill.util.Jvm.runSubprocess(
      mainClass = "mdoc.Main",
      classPath = rp,
      jvmArgs = forkArgs(),
      envArgs = forkEnv(),
      dirParams,
      workingDir = forkWorkingDir(),
      useCpPassingJar = true
    ) // classpath can be long. On windows will barf without passing as Jar
    PathRef(T.dest)
  }

}
