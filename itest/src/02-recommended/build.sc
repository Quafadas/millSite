// build.sc
import $file.plugins

//import $ivy.`io.github.quafadas::mill_scala3_site_mdoc::0.0.5`

import mill._
import mill.scalalib._
import mill.scalalib.publish._
import millSite.SiteModule

trait SimpleModule extends ScalaModule with PublishModule {
  override def scalaVersion = T("3.3.1")
  override def publishVersion: mill.T[String] = "0.0.0"
  override def pomSettings = PomSettings(
    "iTest.desc",
    "iTest.org",
    "iTest.url",
    Seq(License.`Apache-2.0`),
    VersionControl.github("",""),
    Seq()
  )
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

  assert(site.includeApiDocsFromThisModule == false)

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
  println(site.scalaDocOptions())
  assert(site.scalaDocOptions().contains("-snippet-compiler:compile"))
  assert(site.scalaDocOptions().contains("-project-version"))
  assert(site.scalaDocOptions().contains("-social-links:github::iTest.url"))
  assert(site.scalaDocOptions().find(_.startsWith("""libraryDependencies ++= Seq(""")).isDefined)
  assert(site.scalaDocOptions().find(_.contains(""""iTest.org" %% "baz"""")).isDefined)

}
