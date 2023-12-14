package mill.site

import mill._
import mill.scalalib._

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

  // This is the source directory, of the entire site.
  def mdocSources: T[Seq[PathRef]] = T.sources { mdocSourceDir() }

  def transitiveDocSources: T[Seq[PathRef]] =
    T { T.traverse(moduleDeps)(_.docSources)().flatten }

  override def docSources = T {
    if (transitiveDocs()) {
      transitiveDocSources() ++ super.docSources()
    } else {
      super.docSources()
    }
  }

  def mdocSourceDir = T { super.millSourcePath / "docs" }

  // override def ivyDeps = T{ Agg() }

  // override def ivyDeps: T[Agg[Dep]] = T {
  //   super.ivyDeps() ++ Agg(scalaMdocDep()) ++ scalaLibraryIvyDeps() ++ Lib
  //     .scalaCompilerIvyDeps(scalaOrganization(), scalaVersion())
  // }

  def scalaLibrary: T[Dep] = T(
    ivy"org.scala-lang:scala-library:${scalaVersion}"
  )

  def sitePath: T[os.Path] = T { docJar().path / os.up / "javadoc" }

  def sitePathString: T[String] = T { sitePath.toString() }

  // def viaNpm : T[Boolean] = T { true }

  // def generatedMDocs = T{
  //   os.walk(mdoc().path).filter(_.ext == "md")
  // }

  // def generatedStaticSiteBeforeMdoc = T{
  //   os.walk((docJar().path / os.up / "static"))
  // }

  override def docResources = T {
    // os.copy.over(super.docResources(), T.dest / "docs")
    // os.copy.over(mdoc().path , T.dest / "docs" / "_docs")
    println(super.docResources())
    val toProcess = super.docResources()
    for (aDoc <- toProcess) {
      val orig = aDoc.toString()
      val newStub = T.dest.toString()
      val mdPath = orig.replace(mdocSourceDir().toString(), newStub)

      os.copy.over(mdocSourceDir(), T.dest)
    }
    os.copy.over(mdoc().path, T.dest)

    Seq(PathRef(T.dest))
  }

  def npmInstallServeDeps() = T.command {
    os.proc("npm", "install", "-g", "browser-sync").call(stdout = os.Inherit)
  }

  def serveLocal() = T.command {
    os.proc("browser-sync","start",
      "--server",
      "--ss",
      sitePathString(),
      "-w"
    ).call(stdout = os.Inherit)
  }

  // def serveLocal() = T.command {
  //   os.proc(
  //     "browser-sync",
  //     "start",
  //     "--server",
  //     "--ss",
  //     sitePathString(),
  //     "-w"
  //   ).call(stdout = os.Inherit)
  // }

  def moduleName: T[String] = millSourcePath.segments.toList.last.toString()

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
      - run: ./millw ${moduleName()}.docJar
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

  val separator = java.io.File.pathSeparatorChar
  def toArgument(p: Agg[os.Path]) = p.iterator.mkString(s"$separator")

  def mdoc: T[PathRef] = T {
    val cp = (runClasspath()).map(_.path)
    val rp = mDocLibs().map(_.path)
    val dir = T.dest.toIO.getAbsolutePath
    val dirParams = mdocSources()
      .map(pr =>
        Seq(
          s"--in",
          pr.path.toIO.getAbsolutePath,
          "--out",
          dir,
          "--classpath",
          toArgument(cp)
        )
      )
      .iterator
      .flatten
      .toSeq
    mill.util.Jvm.runSubprocess(
      "mdoc.Main",
      rp,
      jvmArgs = forkArgs(),
      envArgs = forkEnv(),
      dirParams,
      workingDir = forkWorkingDir(),
      useCpPassingJar = true
    ) // classpath can be long. On windows will barf without passing as Jar
    PathRef(T.dest)
  }
}
