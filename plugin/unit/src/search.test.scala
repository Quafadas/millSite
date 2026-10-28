// package io.github.quafadas.millSite

// import mill.api.Discover
// import mill.api.Task
// import mill.api.Task.Simple
// import mill.scalalib.UnidocModule
// import mill.testkit.TestRootModule
// import mill.testkit.UnitTester
// import mill.util.TokenReaders.*
// import utest.*

// object SearchTests extends TestSuite:
//   def tests: Tests = Tests {
//     test("search index is generated when enableSearch is true") {
//       object build extends TestRootModule with LaikaModule:
//         val unidocs = new UnidocModule:
//           override def scalaVersion: Simple[String] = Config.scalaVersion
//           override def unidocDocumentTitle: Simple[String] = "Search Test API"

//         lazy val millDiscover = Discover[this.type]
//       end build

//       val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))

//       UnitTester(build, resourceFolder / "search_basic").scoped { eval =>
//         val Right(result) = eval(build.generateSite).runtimeChecked
//         val siteRoot = result.value.path

//         assert(os.exists(siteRoot / "index.html"))

//         val indexFile = siteRoot / "search" / "searchIndex.idx"
//         assert(os.exists(indexFile))
//         assert(os.size(indexFile) > 0L)

//         // SearchUI.helium (not standalone) should inject a search bar into the Helium topNav.
//         // This produces search-helium.css and causes the rendered HTML to contain the
//         // protosearch search input element.
//         assert(os.exists(siteRoot / "search" / "search-helium.css"))

//         val indexHtml = os.read(siteRoot / "index.html")
//         assert(indexHtml.contains("search/search.js"))
//       }
//     }
//   }
// end SearchTests

