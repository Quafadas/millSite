package io.github.quafadas.millSite

import mill.testkit.{TestRootModule, UnitTester}
import mill.api.Discover
import mill.PathRef
import mill.util.TokenReaders.*
import utest.*
import mill.api.Task.Simple

object LaikaTests extends TestSuite {
  def tests: Tests = Tests {
    test("laika works for simple setup") {
      object build extends TestRootModule with LaikaModule {
        // override def scalaVersion: Simple[String] = "3.7.2"

        lazy val millDiscover = Discover[this.type]
      }

      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))      

      UnitTester(build, resourceFolder / "laika_basic").scoped { eval =>
        val Right(result) = eval(build.generateSite)
        println(result)
        val resultPath = result.value.path
        assert(          
            os.exists(resultPath / "index.html")
          )        
      }
    }

  }
}