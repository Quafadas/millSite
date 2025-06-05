package io.github.quafadas.millSite

import mill.testkit.{TestRootModule, UnitTester}
import mill.testkit.IntegrationTester
import utest._
import mill.define.Task
import mill.scalalib.ScalaModule

object SimpleTest extends TestSuite {

  def tests: Tests = Tests {
    test("simple") {

      // object wrapper extends TestRootModule {

      //   object foo extends SimpleModule {
      //     override def scalaVersion = "3.3.4"
      //   }

      //   object simples extends ScalaModule with SiteModule {
      //     override def scalaVersion = "3.3.4"
      //     override def moduleDeps = Seq(foo)

      //   }

      // }

      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))
      val tester = new IntegrationTester(
        daemonMode = true,
        workspaceSourcePath = resourceFolder / "01-simple",
        millExecutable = os.Path(sys.env("MILL_EXECUTABLE_PATH"))
      )

      println(s"resourceFolder: $resourceFolder")
      val res1 = tester.eval("foo.compile")
      assert(res1.isSuccess)
        
      // val simples = wrapper.simples
      // val Right(wrap2) = eval(wrapper.foo.compile)
      // val Right(cpath) = eval(simples.compileClasspath)
      // // println("paths")
      // // println(wrap2.value)
      // // cpath.value.indexed.foreach(p => os.walk(p.path).foreach(println))
      // val Right(mdocRan) = eval(simples.mdoc)

      // val mdocResults = mdocRan.value.path / "_docs" / "some.mdoc.md"
      // assert(
      //   os.exists(mdocResults)
      // )

      // assert(
      //   os.read.lines(mdocResults).exists(_.contains("""// x: Int = 2"""))
      // )

      // val Right(apiOnlyRun) = eval(simples.apiOnlyGen)
      // // val apiGen = apiOnlyRun.value.path / "fake.html"

      // // assert(
      // //   os.exists(apiGen)
      // // )

      // val Right(docOnlyRun) = eval(simples.docOnlyGen)
      // val docOnly = docOnlyRun.value
      // assert(docOnly.docs.nonEmpty)
      // assert(docOnly.docs.size == 2)

      // val Right(liveRun) = eval(simples.live)
      // val liveResults = liveRun.value / "foo.html"
      // val indexRefreshes = os.read(liveRun.value / "index.html")
      // indexRefreshes.contains(
      //   """const sse = new EventSource("/refresh/v1/sse")"""
      // )

      // val Right(toPublish) = eval(simples.publishDocs)
      // val publishPath = toPublish.value.path
      // assert(os.exists(publishPath / "foo.html"))
      // assert(os.exists(publishPath / "index.html"))
      // assert(os.exists(publishPath / "docs" / "some.mdoc.html"))
      // assert(
      //   os.read(publishPath / "docs" / "some.mdoc.html")
      //     .contains("""// fooey: String = "foo""")
      // )

    }
    
  }
}
