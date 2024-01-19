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

  val siteGen = site.live()
  assert(os.exists(siteGen / "baz.html"))
  assert(os.exists(siteGen / "bar.html"))
  assert(os.exists(siteGen / "foo.html"))
  // That images get copied over
  assert(os.exists(siteGen / "images" / "recomend.png"))

  // That the docs are created
  assert(os.exists(siteGen / "docs" / "some.mdoc.html"))

  // That asset paths get fixed passed through mdoc
  assert(
    os.read(siteGen / "docs" / "some.mdoc.html")
      .contains("""src="../images/recomend.png""")
  )

  // That asset paths get fixed when not pre-processed by mdoc
  assert(
    os.read(siteGen / "docs" / "other.html")
      .contains("""src="../images/recomend.png""")
  )

  // That the blog behaves as expected are created
  assert(
    os.exists(
      siteGen / "docs" / "blog" / "2024" / "01" / "01" / "integration-test.html"
    )
  )

  // That scaladoc has some sane defaults
  // println(site.scalaDocOptions())
  assert(site.scalaDocOptions().contains("-snippet-compiler:compile"))
  assert(site.scalaDocOptions().contains("-project-version"))
  assert(site.scalaDocOptions().contains("-social-links:github::https://github/fake/iTest.url"))
  assert(site.scalaDocOptions().sliding(2).contains(Seq("-project", "iTest.url")))
  // assert(site.scalaDocOptions().contains(s"-source-links:github://testOwner/testProject"))
  assert(
    site
      .scalaDocOptions()
      .find(_.startsWith("""libraryDependencies ++= Seq("""))
      .isDefined
  )
  assert(
    site
      .scalaDocOptions()
      .find(_.contains(""""iTest.org" %% "baz""""))
      .isDefined
  )

  // That "final publishing" is not obviously borked.
  val toPublish = site.publishDocs().path
  // println(toPublish)
  assert(
    os.read(toPublish / "docs" / "some.mdoc.html")
      .contains("""src="../images/recomend.png""")
  )

  val browserSyncConfig = site.browserSyncConfig().path
  val conf : String = os.read(browserSyncConfig)
  assert(conf.contains(siteGen.toString()))
  assert(conf.contains(s"""files": ["$siteGen"]"""))
  assert(conf.contains(s"""serveStatic": ["$siteGen"]"""))
  assert(conf.contains(s"""watch": true"""))
  assert(conf.contains(s""""server": true,"""))

  // That "incremental updates" work as expected
  // val preEdit1 = os.stat(siteGen / "docs" / "some.mdoc.html")
  // val preEdit2 = os.stat(siteGen / "docs" / "other.html")
  // println(preEdit2.ctime)

  // println(os.pwd)
  // os.write.over(
  //   os.pwd / "site" / "docs" / "_docs" / "other.md",
  //   "test incremental changes"
  // )
}

// def zincremental() = T.command {
//   println("boo")
//   val siteGen2 = site.siteGen()
//   println(os.read(siteGen2 / "docs" / "other.html"))

  // val postEdit1 = os.stat(siteGen2 / "docs" / "some.mdoc.html")
  // val postEdit2 = os.stat(siteGen2 / "docs" / "other.html")

  // assert(preEdit1.mtime.compareTo(postEdit1.mtime) == 0)
  // assert(
  //   preEdit2.ctime.compareTo(postEdit2.ctime) == 0
  // ) // i..e that file created same time
  // assert(
  //   os.read(siteGen2 / "docs" / "other.html")
  //     .contains("test incremental changes")
  // )
// }
