package io.github.quafadas.millSite

import mill.testkit.{TestRootModule, UnitTester}
import mill.api.Discover
import mill.PathRef
import mill.util.TokenReaders.*
import utest.*
import mill.api.Task.Simple
import mill.scalalib.UnidocModule
import mill.util.JarManifest
import coursier.core.Dependency
import mill.javalib.Dep
import mill.javalib.JvmWorkerModule
import mill.api.ModuleRef
import mill.api.daemon.internal.bsp.BspBuildTarget
import mill.api.Task.Command
import mill.api.Task
import mainargs.Flag

object LaikaTests extends TestSuite {
  def tests: Tests = Tests {
    test("laika works for simple setup") {
      object build extends TestRootModule with LaikaModule {

        val unidocs = new UnidocModule {

          override def scalaVersion: Simple[String] = "3.7.2"

          override def unidocDocumentTitle: Simple[String] = "My Project API"

        }

        lazy val millDiscover = Discover[this.type]
      }

      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))

      UnitTester(build, resourceFolder / "laika_basic").scoped { eval =>
        val Right(result) = eval(build.generateSite)
        println(result)
        // No deps so don't include API
        val Right(includeapi) = eval(build.includeApi)
        assert(!includeapi.value)
        assert(
            os.exists(result.value.path / "index.html")
          )
      }
    }

  }
}