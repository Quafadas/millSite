package mill.site


import mill._
import mill.scalalib._

trait SiteModule extends ScalaModule {

  def scalaVersion = T("3.3.1")

  def transitiveDocs : T[Boolean] = T { true }

  def scalaMdocVersion : T[String] = T("2.5.1")

  def scalaMdocDep : T[Dep] = T(ivy"org.scalameta::mdoc:${scalaMdocVersion()}")

  // This is the source directory, of the entire site.
  def mdocSources : T[Seq[PathRef]] = T.sources { mdocSourceDir() }

  def transitiveDocSources : T[Seq[PathRef]] =
    T{ T.traverse(moduleDeps)(_.docSources)().flatten }

  override def docSources = T {
    if (transitiveDocs()) {
      transitiveDocSources() ++ super.docSources()
    } else {
      super.docSources()
    }
  }

  def mdocSourceDir = T { super.millSourcePath / "docs" }

  override def ivyDeps : T[Agg[Dep]] = T {
    super.ivyDeps() ++ Agg(scalaMdocDep()) ++ scalaLibraryIvyDeps() ++ Lib.scalaCompilerIvyDeps(scalaOrganization(), scalaVersion())
  }

  def scalaLibrary : T[Dep] = T(ivy"org.scala-lang:scala-library:${scalaVersion}")

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
    for (aDoc <- toProcess ) {
      val orig = aDoc.toString()
      val newStub = T.dest.toString()
      val mdPath = orig.replace(mdocSourceDir().toString(), newStub)

      os.copy.over(mdocSourceDir() , T.dest)
    }
    os.copy.over(mdoc().path , T.dest)

    Seq(PathRef(T.dest))
  }

  def serveLocal() = T.command {
    val path = docJar().path / os.up / "javadoc"
    println(path)
    os.proc("npx", "browser-sync", "start", "--server", "--ss", path.toString(), "-w")
    .call(stdout = os.Inherit)
  }

   def mdoc : T[PathRef] = T {
      val cp = (runClasspath()).map(_.path)
      val dir = T.dest.toIO.getAbsolutePath
      val dirParams = mdocSources().map(pr =>
        Seq(
          s"--in", pr.path.toIO.getAbsolutePath,
          "--out",  dir,
          "--classpath", cp.map(_.toIO.getAbsolutePath).mkString(":"),
        )
        ).iterator.flatten.toSeq
      mill.util.Jvm.runSubprocess("mdoc.Main", cp, Seq.empty, Map.empty, dirParams)
      PathRef(T.dest)
    }
}

