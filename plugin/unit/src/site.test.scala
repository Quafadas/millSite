package io.github.quafadas.millSite

import mill.testkit.{TestRootModule, UnitTester}
import mill.api.Discover
import mill.PathRef
import mill.util.TokenReaders.*
import utest.*
import mill.api.Task.Simple

object SiteTests extends TestSuite {
  def tests: Tests = Tests {
    test("Basic site processes mdoc") {
      object build extends TestRootModule with SiteModule {

        lazy val millDiscover = Discover[this.type]
      }

      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))

      UnitTester(build, resourceFolder / "simple_site").scoped { eval =>

        val Right(resources) = eval(build.resources)
        val Right(resourcesMdoc) = eval(build.mdocModule.resources)
        val Right(compileResourcesMdoc) = eval(build.mdocModule.compileResources)


        assert(resourcesMdoc.value.length == 2) // should include the site module resourceDir as well
        assert(compileResourcesMdoc.value.length == 2) // should include the site module resourceDir as well

        val Right(result) = eval(build.siteGen)

        val resultPath = result.value.path
        assert(
            os.exists(resultPath / "index.html")
        )

      }
    }
  }
}