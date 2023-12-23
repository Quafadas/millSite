// build.sc
import $file.plugins

//import $ivy.`io.github.quafadas::mill_scala3_site_mdoc::0.0.5`

import mill._
import mill.scalalib._

import millSite.SiteModule

trait SimpleModule extends ScalaModule {
  override def scalaVersion = T("3.3.1")
}

object baz extends SimpleModule

object bar extends SimpleModule {

  def moduleDeps = Seq(baz)

}

object foo extends SimpleModule {

  def moduleDeps = Seq(bar)

}

// Single module setup
object site extends SiteModule {
  override def scalaVersion = T("3.3.1")
  def moduleDeps = Seq(foo)
}

def verify() = T.command {

  foo.compile()

  assert(site.includeApiDocsFromThisModule() == false)

  // println(site.walkTransitiveDeps)
  // println(site.transitiveDocSources())

  val siteGen = site.siteGen()
  assert(os.exists(siteGen / "baz.html"))
  assert(os.exists(siteGen / "bar.html"))
  assert(os.exists(siteGen / "foo.html"))
  // That images get copied over
  assert(os.exists(siteGen / "images" / "recomend.png"))

  assert(os.exists(siteGen / "docs" / "some.mdoc.html"))

  assert(
    os.read(siteGen / "docs" / "some.mdoc.html")
      .contains("""src="../images/recomend.png""")
  )

  assert(
    os.exists(
      siteGen / "docs" / "blog" / "2024" / "01" / "01" / "integration-test.html"
    )
  )

}
