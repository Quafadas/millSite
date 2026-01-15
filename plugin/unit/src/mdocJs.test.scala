package io.github.quafadas.millSite

import mill.testkit.{TestRootModule, UnitTester}
import mill.api.Discover
import mill.PathRef
import mill.util.TokenReaders.*
import utest.*
import mill.api.Task.Simple
import mill.scalajslib.ScalaJSModule
import mill.scalajslib.api.ESModuleImportMapping
import mill.api.Task

object MdocJsTests extends TestSuite {
  def tests: Tests = Tests {
    test("mdoc basic processes mdoc") {

      inline val scalaVersionS = "3.8.0"
      inline val scalaJsVersionS = "1.20.2"

      object build extends TestRootModule  {
        val jvm = new MdocModule {
          override def scalaVersion: Simple[String] = Task{scalaVersionS}
          override val jsSiteModule = new SiteJSModule:
            override def scalaVersion: Simple[String] = Task{scalaVersionS}
            override def scalaJSVersion: Simple[String] = Task{scalaJsVersionS}
            override def scalaJSImportMap: Simple[Seq[ESModuleImportMapping]] = Seq(
              ESModuleImportMapping.Prefix("BarJsPackage", "bar_js_package.js")
            )
            override def moduleDeps = Seq(js)
          }

        val js = new ScalaJSModule{
          override def scalaVersion: Simple[String] = Task{scalaVersionS}
          override def scalaJSVersion: Simple[String] = Task{scalaJsVersionS}
        }

        lazy val millDiscover = Discover[this.type]
      }

      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))

      UnitTester(build, resourceFolder / "mdoc_js").scoped { eval =>
        //TODO unit test that the import map is actually used
        val Right(importFlags) = eval(build.jvm.jsSiteModule.mdocJsImportMap)

        val Right(result) = eval(build.jvm.mdoc2)
        println(result)
        val resultPath = result.value.path
        assert(
            os.exists(resultPath / "hi.md")
          )

        assert(
          os.read.lines(resultPath / "hi.md").mkString("").contains("BarJsPackage.BarJsObj.barMethod")
        )

      }
    }
  }
}