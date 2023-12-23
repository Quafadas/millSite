import $file.plugins
import mill._
import mill.scalalib._
import millSite.SiteModule

// Single module setup
object simples extends SiteModule {
  override def scalaVersion = T("3.3.1")
}

def verify() = T.command {

  val compile = simples.compile().classes

  assert(os.exists(compile.path / "foo" / "Foo.class"))

  val res: os.Path = simples.mdoc().path / "_docs" / "some.mdoc.md"
  if (!os.exists(res))
    throw new Exception(
      s"Expected mdoc file <${res.toIO.getAbsolutePath}> does not exist"
    )

  val content = os.read.lines(res)
  if (!content.exists(_.contains("""// x: Int = 2""")))
    throw new Exception(
      s"Generated md file does not contain expected REPL output"
    )

  if (!content.exists(_.contains("""// fooey: String = "foo""")))
    throw new Exception(
      s"Generated md file does not contain expected REPL output"
    )

  assert(simples.includeApiDocsFromThisModule() == true)

  simples
    .transitiveDocSources()
    .map(_.path.toString())
    .find(_.contains("01-simple/out/simples/compile.dest/classes"))

  val api = simples.apiOnlyGen()
  assert(os.exists(api / "foo.html"))

  val docOnly = simples.docOnlyGen()
  assert(os.exists(docOnly / "foo.html"))

  val site = simples.siteGen()
  assert(os.exists(site / "foo.html"))
  // ensure that the docs page, is the default page
  // assert(os.read(site / "index.html" ).contains("""h100 selected" href="docs/index.html"""))

}
