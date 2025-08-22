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
    test("mdoc basic processes mdoc") {



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
        println("Compiled API:")
        println(api)

        val Right(unidoc) = eval(build.laika.unidocs.unidocLocal)
        println("Compiled Unidoc:")
        println(unidoc.value.path)

        println("Mdoc")
        val Right(mdocs) = eval(build.mdocModule.mdocT)
        println(mdocs.value.path)

        val Right(site) = eval(build.siteGen)
        println(site.value.path)

      //   val Right(result) = eval(build.siteGen)


      //   println(result)
      //   val resultPath = result.value.path
      //   println(resultPath)
      // }
      }
    }
  }
}