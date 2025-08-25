package io.github.quafadas.millSite

import mill.testkit.{TestRootModule, UnitTester}
import mill.api.Discover
import mill.PathRef
import mill.util.TokenReaders.*
import utest.*
import mill.api.Task.Simple
import mill.*
import mill.scalalib.*

object UnidocTests extends TestSuite {
  def tests: Tests = Tests {
    test("unidoc included in site basic processes mdoc") {



      object build extends TestRootModule with SiteModule {
        lazy val common: ScalaModule = new ScalaModule {
          def scalaVersion: Simple[String] = "3.7.2"
        }

        override def unidocDeps: Seq[JavaModule] = Seq(common)

        lazy val millDiscover = Discover[this.type]
      }

      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))

      UnitTester(build, resourceFolder / "unidoc_example").scoped { eval =>

        val Right(api) = eval(build.common.compile)


        val Right(withApi) = eval(build.laika.includeApi)


        val Right(checkHelium) = eval(build.laika.helium)
        val Right(checkApi) = eval(build.laika.stageSite)
        val apiDocPath = checkApi.value.path

        assert(os.exists(apiDocPath / "api" / "index.html"))

        val Right(unidoc) = eval(build.laika.unidocs.unidocLocal)
        // println(unidoc.value.path)

        val Right(mdocs) = eval(build.mdocModule.mdoc2)

        val Right(site) = eval(build.siteGen)
        // If the "with API "

      //   val Right(result) = eval(build.siteGen)


      //   println(result)
      //   val resultPath = result.value.path
      //   println(resultPath)
      // }
      }
    }
  }
}