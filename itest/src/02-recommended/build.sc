// build.sc
import $file.plugins
import $file.SimpleModule

//import $ivy.`io.github.quafadas::mill_scala3_site_mdoc::0.0.5`

import mill._
import mill.scalalib._
import mill.scalalib.publish._
import io.github.quafadas.millSite.SiteModule
import SimpleModule.SimpleModule

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
    os.read(siteGen / "docs" / "other.html")
      .contains("""src="../images/recomend.png""")
  )

  assert(
    os.exists(
      siteGen / "docs" / "blog" / "2024" / "01" / "01" / "integration-test.html"
    )
  )
  println(site.scalaDocOptions())
  assert(site.scalaDocOptions().contains("-snippet-compiler:compile"))
  assert(site.scalaDocOptions().contains("-project-version"))
  assert(site.scalaDocOptions().contains("-social-links:github::iTest.url"))
  // assert(site.scalaDocOptions().contains(s"-source-links:github://testOwner/testProject"))
  assert(site.scalaDocOptions().find(_.startsWith("""libraryDependencies ++= Seq(""")).isDefined)
  assert(site.scalaDocOptions().find(_.contains(""""iTest.org" %% "baz"""")).isDefined)


  val toPublish = site.publishDocs().path
  println(toPublish)
  assert(
    os.read(toPublish / "docs" / "some.mdoc.html")
      .contains("""src="../images/recomend.png""")
  )
}
