package io.github.quafadas.millSite

import mill.testkit.{TestRootModule, UnitTester}
import mill.api.Discover
import mill.PathRef
import mill.util.TokenReaders.*
import utest.*
import mill.api.Task.Simple
import mill.scalajslib.ScalaJSModule
import mill.scalajslib.api.ESModuleImportMapping

object MdocJsTests extends TestSuite {
  def tests: Tests = Tests {
    test("mdoc basic processes mdoc") {

      object build extends TestRootModule  {
        val jvm = new MdocModule {
          override def scalaVersion: Simple[String] = "3.7.2"
          override val jsSiteModule = new SiteJSModule:
            override def scalaVersion: Simple[String] = "3.7.2"
            override def scalaJSVersion: Simple[String] = "1.20.1"
            override def scalaJSImportMap: Simple[Seq[ESModuleImportMapping]] = Seq(
              ESModuleImportMapping.Prefix("BarJsPackage", "bar_js_package.js")
            )
            override def moduleDeps = Seq(js)
          }

        val js = new ScalaJSModule{
          override def scalaVersion: Simple[String] = "3.7.2"
          override def scalaJSVersion: Simple[String] = "1.20.1"
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