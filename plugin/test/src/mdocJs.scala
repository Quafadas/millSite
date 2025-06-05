// package io.github.quafadas.millSite

// import mill.testkit.{TestRootModule, UnitTester}
// import utest._
// import mill.define.Task
// import mill.scalalib.ScalaModule
// import mill._
// import mill.scalalib._

// object MdocJsTest extends TestSuite {

//   def tests: Tests = Tests {
//     test("simple") {

//       val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))

//       object wrapper extends TestRootModule {

//         object foo extends SimpleModule

//         object simpleJs extends SiteJSModule {
//           override def scalaVersion = "3.3.3"
//           override def scalaJSVersion = "1.16.0"
//           override def mvnDeps = Agg(
//             mvn"com.raquo::laminar::17.0.0"
//           )
//         }

//         object withJsProject extends SiteModule {
//           override def scalaVersion = "3.3.3"
//           override val jsSiteModule = simpleJs
//           override def moduleDeps = Seq(foo)
//         }

//       }

//       val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))
//       println(s"resourceFolder: $resourceFolder")
//       UnitTester(wrapper, resourceFolder / "04-mdocjs").scoped { eval =>
//         val Right(mdocPropsV) = eval(wrapper.simpleJs.mdocJsProperties)
//         assert(os.exists(mdocPropsV.value.path / "mdoc.properties"))

//         val mdocProperties = os.read(mdocPropsV.value.path / "mdoc.properties")
//         assert(mdocProperties.contains("""js-scalac-options"""))
//         assert(mdocProperties.contains("""js-linker-classpath"""))
//         assert(mdocProperties.contains("""js-classpath"""))
//         assert(mdocProperties.contains("""js-module-kind=ESModule"""))

//         val Right(mdocOutV) = eval(wrapper.withJsProject.mdoc)
//         val mdocOut = mdocOutV.value.path
//         assert(os.exists(mdocOut / "_docs" / "some.mdoc.md"))
//         assert(
//           os.exists(mdocOut / "_docs" / "some.mdoc.md.js")
//         ) // subRelTo puts these in the right place.
//         assert(os.exists(mdocOut / "_docs" / "mdoc.js"))

//         val Right(liveOutV) = eval(wrapper.withJsProject.live)
//         val site = liveOutV.value
//         assert(os.exists(site / "docs" / "some.mdoc.html"))
//         assert(
//           os.read(site / "docs" / "some.mdoc.html").contains("""src="mdoc.js""")
//         )
//         assert(os.exists(site / "docs" / "some.mdoc.md.js"))
//         assert(os.exists(site / "docs" / "mdoc.js"))

//         val Right(toPublishV) = eval(wrapper.withJsProject.publishDocs)
//         val toPublish = toPublishV.value.path
//         assert(
//           os.read(toPublish / "docs" / "some.mdoc.html")
//             .contains("""src="mdoc.js""")
//         )
//         assert(os.exists(toPublish / "docs" / "some.mdoc.md.js"))
//         assert(os.exists(toPublish / "docs" / "mdoc.js"))

//       }
//     }
//   }
// }
