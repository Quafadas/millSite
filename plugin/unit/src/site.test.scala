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
        val Right(result) = eval(build.siteGen)
        println(result)
        val resultPath = result.value.path
        eval(build.dezombify)
        assert(
            os.exists(resultPath / "index.html")
        )

      }
    }
  }
}