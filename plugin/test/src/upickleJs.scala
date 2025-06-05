// package io.github.quafadas.millSite

// import mill.testkit.{TestRootModule, UnitTester}
// import utest._
// import mill.define.Task
// import mill.scalalib.ScalaModule
// import mill._
// import mill.scalalib._
// import mill.scalajslib.ScalaJSModule

// object UpickleJsTest extends TestSuite {

//   def tests: Tests = Tests {
//     test("upickle JS") {

//       object wrapper extends TestRootModule {

//         val scalaJsVersion = "1.19.0"

//         val upickle = mvn"com.lihaoyi::upickle::4.1.0"

//         object foo extends SimpleModule

//         object jsDep extends SimpleModule with ScalaJSModule {

//           override def scalaJSVersion = scalaJsVersion
//           override def mvnDeps = Agg(
//             mvn"org.scala-js::scalajs-dom::2.8.0",
//             upickle
//           )

//         }

//         object simpleJs extends SiteJSModule {

//           override def moduleDeps = Seq(jsDep)
//           override def scalaVersion = "3.7.0-RC4"
//           override def scalaJSVersion = scalaJsVersion
//           // override def ivyDeps = Agg(
//           //   ivy"org.scala-js::scalajs-dom::2.8.0",
//           //   upickle
//           // )
//         }

//         object withJsProject extends SiteModule {
//           override def scalaVersion = "3.7.0-RC4"
//           override val jsSiteModule = simpleJs
//           override def moduleDeps = Seq(foo)
//         }

//       }

//       val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))
//       println(s"resourceFolder: $resourceFolder")
//       UnitTester(wrapper, resourceFolder / "05-upickleJs").scoped { eval =>
//         val Right(mdocOutV) = eval(wrapper.withJsProject.mdoc)
//         val mdocOut = mdocOutV.value.path
//         assert(
//           os.exists(mdocOut / "_docs" / "some.mdoc.md.js")
//         )
//         // This can only come from the JS module dependancy
//         val jsCode = os.read(mdocOut / "_docs" / "some.mdoc.md.js")
//         assert(jsCode.contains("""fooly", "barly"""))

//       }
//     }
//   }
// }
