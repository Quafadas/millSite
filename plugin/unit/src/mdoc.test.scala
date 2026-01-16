package io.github.quafadas.millSite

import mill.testkit.{TestRootModule, UnitTester}
import mill.api.Discover
import mill.PathRef
import mill.util.TokenReaders.*
import utest.*
import mill.api.Task.Simple

object MdocTests extends TestSuite:
  def tests: Tests = Tests {
    test("mdoc basic processes mdoc") {
      object build extends TestRootModule with MdocModule:
        override def scalaVersion: Simple[String] = "3.8.0"

        override def siteVariables: Simple[Seq[(String, String)]] = Seq(("VERSION", "1.2.3"))

        lazy val millDiscover = Discover[this.type]
      end build

      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))

      UnitTester(build, resourceFolder / "mdoc_basic").scoped { eval =>
        val Right(result) = eval(build.mdoc2).runtimeChecked
        println(result)
        val resultPath = result.value.path
        assert(
          os.exists(resultPath / "hi.mdoc.md")
        )
        assert(
          os.exists(resultPath / "random" / "folder" / "nested.mdoc.md")
        )
        assert(
          os.read.lines(resultPath / "hi.mdoc.md").mkString("").contains("FooPackage.FooObj.fooMethod")
        )
        assert(
          os.read.lines(resultPath / "hi.mdoc.md").mkString("").contains("// res1: Int = 42")
        )
        assert(
          os.read.lines(resultPath / "hi.mdoc.md").mkString("").contains("1.2.3")
        )

        // Run again and assert mdoc worker was not invoked (in-memory cache used)
        val Right(countAfterFirst) = eval(build.mdocWorkerRunCount).runtimeChecked
        val Right(result2) = eval(build.mdoc2).runtimeChecked
        val Right(countAfterSecond) = eval(build.mdocWorkerRunCount).runtimeChecked
        assert(countAfterSecond.value == countAfterFirst.value)
      }
    }
  }
end MdocTests
