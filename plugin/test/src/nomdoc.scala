// package io.github.quafadas.millSite

// import mill.testkit.{TestRootModule, UnitTester}
// import utest._
// import mill.define.Task
// import mill.scalalib.ScalaModule
// import mill._
// import mill.scalalib._

// object NoMdocTest extends TestSuite {

//   def tests: Tests = Tests {
//     test("simple") {

//       object wrapper extends TestRootModule {

//         object simples extends SimpleModule

//         object site extends SiteModule {
//           override def scalaVersion = Task("3.3.1")
          
//           override def moduleDeps = Seq(simples)
//         }

//       }

//       val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))
//       println(s"resourceFolder: $resourceFolder")
//       UnitTester(wrapper, resourceFolder / "03-no_mdoc").scoped { eval =>
//         val Right(liveOutV) = eval(wrapper.site.live)
//         val Right(mdocV) = eval(wrapper.site.mdoc)
//         assert(os.exists(mdocV.value.path))
//         assert(os.exists(liveOutV.value / "docs" / "some.html"))
//       }
//     }
//   }
// }
