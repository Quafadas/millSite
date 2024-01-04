import $file.plugins
import $file.SimpleModule
import mill._
import mill.scalalib._
import io.github.quafadas.millSite.SiteModule


object foo extends SimpleModule.SimpleModule {

}

// Single module setup
object simples extends SiteModule {
  override def scalaVersion = foo.scalaVersion()
  def moduleDeps = Seq(foo)
}

def verify() = T.command {

  val compile = simples.compileClasspath()

  assert(compile.exists(_.path.toString().contains("out/foo/compile.dest/classes")))

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

  simples
    .docSources()
    .map(_.path.toString())
    .find(_.contains("01-simple/out/simples/compile.dest/classes"))

  val api = simples.apiOnlyGen()
  assert(os.exists(api.path / "foo.html"))

  val api2 = simples.apiOnlyGen()
  assert(api2 == api) // i.e. that caching works.

  val docOnly = simples.docOnlyGen()
  assert(docOnly.docs.nonEmpty)
  assert(docOnly.docs.size == 2)

  val site = simples.live()
  assert(os.exists(site / "foo.html"))

  val toPublish = simples.publishDocs().path
  println(toPublish)
  assert(os.exists(toPublish / "foo.html"))
  assert(os.exists(toPublish / "index.html"))
  assert(os.exists(toPublish / "docs"/"some.mdoc.html"))
  assert(os.read(toPublish / "docs"/"some.mdoc.html")
    .contains("""// fooey: String = "foo"""))
}
