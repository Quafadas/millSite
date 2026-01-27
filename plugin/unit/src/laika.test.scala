package io.github.quafadas.millSite

import mainargs.Flag
import mill.api.Discover
import mill.api.Task
import mill.api.Task.Simple
import mill.scalalib.UnidocModule
import mill.testkit.TestRootModule
import mill.testkit.UnitTester
import mill.util.TokenReaders.*
import utest.*

object LaikaTests extends TestSuite:
  def tests: Tests = Tests {
    test("laika works for simple setup") {
      object build extends TestRootModule with LaikaModule:

        val unidocs = new UnidocModule:

          override def scalaVersion: Simple[String] = Config.scalaVersion

          override def unidocDocumentTitle: Simple[String] = "My Project API"

        lazy val millDiscover = Discover[this.type]
      end build

      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))

      UnitTester(build, resourceFolder / "laika_basic").scoped { eval =>
        val Right(result) = eval(build.generateSite).runtimeChecked
        println(result)
        // No deps so don't include API
        val Right(includeapi) = eval(build.includeApi).runtimeChecked
        assert(!includeapi.value)
        assert(
          os.exists(result.value.path / "index.html")
        )
      }
    }

  }
end LaikaTests
