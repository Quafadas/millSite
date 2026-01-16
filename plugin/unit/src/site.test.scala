package io.github.quafadas.millSite

import mill.testkit.{TestRootModule, UnitTester}
import mill.api.Discover
import mill.PathRef
import mill.util.TokenReaders.*
import utest.*
import mill.api.Task.Simple

object SiteTests extends TestSuite:
  def tests: Tests = Tests {
    test("Basic site processes mdoc") {
      object build extends TestRootModule with SiteModule:
        override def scalaVersion: Simple[String] = "3.8.0"
        override def mdocSiteVariables: Simple[Seq[(String, String)]] = Seq("VERSION" -> "0.0.0")

        override def forkArgs: Simple[Seq[String]] = Seq("-Duser.name=test-user")

        lazy val millDiscover = Discover[this.type]
      end build

      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))

      UnitTester(build, resourceFolder / "simple_site").scoped { eval =>

        val Right(resources) = eval(build.resources).runtimeChecked
        val Right(resourcesMdoc) = eval(build.mdocModule.resources).runtimeChecked
        val Right(compileResourcesMdoc) = eval(build.mdocModule.compileResources).runtimeChecked
        val Right(siteVariablesMdoc) = eval(build.mdocModule.siteVariables).runtimeChecked
        val Right(forkArgsMdoc) = eval(build.mdocModule.forkArgs).runtimeChecked

        assert(forkArgsMdoc.value == Seq("-Duser.name=test-user"))
        assert(siteVariablesMdoc.value == Seq("VERSION" -> "0.0.0"))

        assert(resourcesMdoc.value.length == 2) // should include the site module resourceDir as well
        assert(compileResourcesMdoc.value.length == 2) // should include the site module resourceDir as well

        val Right(result) = eval(build.siteGen).runtimeChecked

        val resultPath = result.value.path
        assert(
          os.exists(resultPath / "index.html")
        )

      }
    }
  }
end SiteTests
