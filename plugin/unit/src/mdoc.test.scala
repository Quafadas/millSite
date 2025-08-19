package io.github.quafadas.millSite

import mill.testkit.{TestRootModule, UnitTester}
import mill.api.Discover
import mill.PathRef
import mill.util.TokenReaders.*
import utest.*
import mill.api.Task.Simple

object UnitTests extends TestSuite {
  def tests: Tests = Tests {
    test("mdocBasic") {
      object build extends TestRootModule with MdocModule {
        override def scalaVersion: Simple[String] = "3.7.2"

        lazy val millDiscover = Discover[this.type]
      }

      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))
      println(resourceFolder)
      UnitTester(build, resourceFolder / "mdoc_basic").scoped { eval =>
        // Evaluating tasks by direct reference
        println("--- CHECKING inputs")
        val Right(compiled) = eval(build.compile) //
        println(compiled)
        val Right(aaaahhhh) = eval(build.docDir)
        println(aaaahhhh)
        os.walk(aaaahhhh.value.path).toSeq.foreach(println)
        val Right(sources) = eval(build.mdocFiles)
        println("mdoc files")
        println(sources)
        sources.value.foreach(println)

        println("classPath")
        println(eval(build.compileClasspath))

        println("WHERE FROM")
        println()
        println(eval("""compileClasspath"""))

        val Right(result) = eval(build.mdoc)
        println("--- CHECKING outputs")
        println(result)
        os.walk(result.value.path).toSeq.foreach(println)

        // // Evaluating tasks by passing in their Mill selector
        // val Right(result2) = eval("resources")
        // val Seq(pathrefs: Seq[PathRef]) = result2.value
        // assert(
        //   pathrefs.exists(pathref =>
        //     os.exists(pathref.path / "line-count.txt") &&
        //       os.read(pathref.path / "line-count.txt") == "18"
        //   )
        // )
      }
    }
  }
}