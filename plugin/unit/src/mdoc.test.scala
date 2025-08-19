package io.github.quafadas.millSite

import mill.testkit.{TestRootModule, UnitTester}
import mill.api.Discover
import mill.PathRef
import mill.util.TokenReaders.*
import utest.*
import mill.api.Task.Simple

object MdocTests extends TestSuite {
  def tests: Tests = Tests {
    test("mdoc basic processes mdoc") {
      object build extends TestRootModule with MdocModule {
        override def scalaVersion: Simple[String] = "3.7.2"

        lazy val millDiscover = Discover[this.type]
      }

      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))      

      UnitTester(build, resourceFolder / "mdoc_basic").scoped { eval =>        
        val Right(result) = eval(build.mdocOnly)
        // println(result)
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
      }
    }

    test("Other files pass straight through basic setup") {
      object build extends TestRootModule with MdocModule {
        override def scalaVersion: Simple[String] = "3.7.2"

        lazy val millDiscover = Discover[this.type]
      }

      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))      

      UnitTester(build, resourceFolder / "mdoc_basic").scoped { eval =>        
        val Right(result) = eval(build.mdFiles)
        // println(result)
        assert(result.value.length == 2)
        
      }
    }

    test("all files available") {
      object build extends TestRootModule with MdocModule {
        override def scalaVersion: Simple[String] = "3.7.2"

        lazy val millDiscover = Discover[this.type]
      }

      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))      

      UnitTester(build, resourceFolder / "mdoc_basic").scoped { eval =>        
        val Right(result) = eval(build.mdoc)

        assert(os.walk(result.value.path).filter(os.isFile).toSeq.size == 4)
        // .foreach { file =>
        //   println(s"Found file: ${file}")
        // }

      }
    }
  }
}