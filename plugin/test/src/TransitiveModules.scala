package io.github.quafadas.millSite

import mill.testkit.{TestBaseModule, UnitTester}
import utest._
import mill.define.Task
import mill.scalalib.ScalaModule

object TransitiveTests extends TestSuite {
  def tests: Tests = Tests {

    object wrapper extends TestBaseModule {

      object baz extends SimpleModule {}

      object bar extends SimpleModule {
        override def moduleDeps = Seq(baz)
      }

      object foo extends SimpleModule {
        override def moduleDeps = Seq(bar)
      }

      // Single module setup
      object site extends SiteModule {
        override def scalaVersion = Task("3.3.4")
        override def moduleDeps = Seq(foo)
      }
    }

    val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))

    test("compileCpArgs") {

      UnitTester(wrapper, resourceFolder / "02-real").scoped { eval =>
        val Right(scalaDocOptsV) = eval(wrapper.site.scalaDocOptions)
        val scalaDocOpts = scalaDocOptsV.value
        assert(scalaDocOpts.contains("-snippet-compiler:compile"))
        assert(scalaDocOpts.contains("-project-version"))
        assert(
          scalaDocOpts.contains(
            "-social-links:github::https://github/fake/iTest.url"
          )
        )
        assert(scalaDocOpts.sliding(2).contains(Seq("-project", "iTest.url")))
        assert(
          scalaDocOpts
            .find(_.startsWith("""libraryDependencies ++= Seq("""))
            .isDefined
        )
        assert(
          scalaDocOpts
            .find(_.contains(""""iTest.org" %% "baz""""))
            .isDefined
        )

        val Right(siteGenPath) = eval(wrapper.site.live)
        val siteGen = siteGenPath.value
        assert(os.exists(siteGen / "baz.html"))
        assert(os.exists(siteGen / "bar.html"))
        assert(os.exists(siteGen / "foo.html"))
        assert(os.exists(siteGen / "images" / "recomend.png"))
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

        val Right(toPublish) = eval(wrapper.site.publishDocs)
        // println(toPublish)
        assert(
          os.read(toPublish.value.path / "docs" / "some.mdoc.html")
            .contains("""src="../images/recomend.png""")
        )

        val Right(browserSyncConfig) = eval(wrapper.site.browserSyncConfig)
        val conf: String = os.read(browserSyncConfig.value.path)
        assert(conf.contains(siteGen.toString()))
        assert(conf.contains(s"""files": ["$siteGen"]"""))
        assert(conf.contains(s"""serveStatic": ["$siteGen"]"""))
        assert(conf.contains(s"""watch": true"""))
        assert(conf.contains(s""""server": true,"""))

      }

    }

  }
}
