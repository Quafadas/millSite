package io.github.quafadas.millSite

import mill.*
import mill.api.Discover
import mill.api.Task.Simple
import mill.scalalib.*
import mill.testkit.TestRootModule
import mill.testkit.UnitTester
import utest.*

object UnidocTests extends TestSuite:
  def tests: Tests = Tests {
    test("unidoc included in site basic processes mdoc") {

      object build extends TestRootModule with SiteModule:
        override def scalaVersion: Simple[String] = Config.scalaVersion
        lazy val common: ScalaModule = new ScalaModule:
          def scalaVersion: Simple[String] = Config.scalaVersion

        override def unidocDeps: Seq[JavaModule] = Seq(common)

        lazy val millDiscover = Discover[this.type]
      end build

      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))

      UnitTester(build, resourceFolder / "unidoc_example").scoped { eval =>

        val Right(_) = eval(build.common.compile).runtimeChecked

        val Right(_) = eval(build.laika.includeApi).runtimeChecked

        val Right(_) = eval(build.laika.helium).runtimeChecked
        val Right(checkApi) = eval(build.laika.stageSite).runtimeChecked
        val apiDocPath = checkApi.value.path

        assert(os.exists(apiDocPath / "api" / "index.html"))

        val Right(_) = eval(build.laika.unidocs.unidocLocal).runtimeChecked
        // println(unidoc.value.path)

        val Right(_) = eval(build.mdocModule.mdoc2).runtimeChecked

        val Right(_) = eval(build.siteGen).runtimeChecked
        // If the "with API "

      //   val Right(result) = eval(build.siteGen)

      //   println(result)
      //   val resultPath = result.value.path
      //   println(resultPath)
      // }
      }
    }
  }
end UnidocTests
