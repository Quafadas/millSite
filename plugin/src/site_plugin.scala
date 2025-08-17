package io.github.quafadas.millSite

import mill.*
import mill.scalalib.*
import os.Path
import mill.api.Task.Simple
import fs2.concurrent.Topic
import cats.effect.IO
// import mill.scalajslib.*
// import coursier.maven.MavenRepository
// import mill.api.Result
// import mill.util.Jvm.createJar
// import mill.define.PathRef
// import mill.scalalib.api.CompilationResult
// // import de.tobiasroeser.mill.vcs.version.VcsVersion
// import scala.util.Try
// import mill.scalalib.publish.PomSettings
// import mill.scalalib.publish.License
// import mill.scalalib.publish.VersionControl
// import os.SubPath
// import ClasspathHelp.*
import cats.effect.unsafe.implicits.global

trait SiteModule extends Module:

  val updateServer = Topic[IO, Unit].unsafeRunSync()  

  lazy val mdocModule : MdocModule = ???

  lazy val unidocs : UnidocModule = ???

  lazy val laika = new LaikaPlugin {

    override def docDir: Path = mdocModule.mdocDir
    override def inputDir: Simple[PathRef] = mdocModule.mdoc()
    override def baseUrl: Simple[String] = 
      unidocs.unidocSourceUrl().getOrElse("no path")      
      
  }

  def siteGen = Task{
    val mdocs = mdocModule.mdoc()
    val api = unidocs.unidocSite()
    laika.generateSite()
  }

  

//   val jsSiteModule: SiteJSModule =
//     new SiteJSModule:
//       override def scalaVersion = Task("3.3.5")
//       override def scalaJSVersion = Task("1.19.0")

// // // TODO
// //   // def latestVersion = Task {
// //   //   VcsVersion.vcsState().lastTag.getOrElse("0.0.0").replace("v", "")
// //   // }

// //   // def scalaVersion = T("3.3.1")

// //   /** If we're given module dependancies, then assume we probably don't want to include source files in the doc site, in
// //     * the published API docs.
// //     */
// //   def checkModuleModule: Unit =
// //     if moduleDeps.length == 0 then throw new Exception("You must provide at least one module dependency")

//   /** Finds everything that is going to get published
//     *
//     * @return
//     */
//   def findAllTransitiveDeps: Set[JavaModule] =
//     def loop(
//         acc: Set[JavaModule],
//         current: JavaModule
//     ): Set[JavaModule] =
//       val newAcc = acc + current
//       val newDeps = current.moduleDeps
//         .filter(_.isInstanceOf[PublishModule])
//         .filterNot(newAcc.contains(_))
//         .toSet
//       if newDeps.isEmpty then newAcc
//       else newDeps.foldLeft(newAcc)((acc, dep) => loop(acc, dep))
//       end if
//     end loop
//     moduleDeps.foldLeft(Set[JavaModule]())((acc, dep) => loop(acc, dep))
//   end findAllTransitiveDeps

//   override def docSources = Task {
//     Task.traverse(findAllTransitiveDeps.toSeq)(_.docSources)().flatten
//   }

//   override def compileClasspath = Task {
//     Task.traverse(findAllTransitiveDeps.toSeq)(_.compileClasspath)().flatten ++ super.compileClasspath()
//   }

//   def artefactNames = Task.traverse(findAllTransitiveDeps.toSeq)(_.artifactName)

//   def scalaMdocVersion: T[String] = Task(Versions.mdocVersion)

//   def mdocDep: T[Seq[Dep]] = Task(
//     Seq(
//       mvn"org.scalameta::mdoc-js:${scalaMdocVersion()}",
//       mvn"org.scalameta::mdoc:${scalaMdocVersion()}"
//         .exclude("org.scala-lang" -> "scala3-compiler_3")
//         .exclude("org.scala-lang" -> "scala3-library_3"),
//       mvn"org.scala-lang::scala3-compiler:${scalaVersion()}",
//       mvn"org.scala-lang::scala3-library:${scalaVersion()}",
//       mvn"org.scala-lang::tasty-core:${scalaVersion()}",
//       mvn"org.scala-lang.modules::scala-xml:2.1.0"
//     )
//   )

//   def browserSync() = Task.Command {
//     val conf = browserSyncConfig()
//     os.proc("browser-sync", "start", "--config", conf.path)
//       .call(
//         cwd = super.moduleDir,
//         stdin = os.Inherit,
//         stdout = os.Inherit,
//         stderr = os.Inherit
//       )
//     "Finished browser-sync"
//   }

//   def browserSyncBackground() =
//     runBackgroundTask(
//       browserSync()
//     )

//   // def serveBackground = Task.Command {
//   //   runBackgroundTask(
//   //     serve
//   //   )
//   // }

//   def serve() = Task.Command {
//     val sitePath = live()
//     // val port_ = port.getOrElse("8080")

//     val res = os
//       .proc(
//         "cs",
//         "launch",
//         "io.github.quafadas::sjsls:0.2.5",
//         "--",
//         "--path-to-index-html",
//         sitePath.toString(),
//         "--build-tool",
//         "none",
//         "--browse-on-open-at",
//         "/docs/index.html",
//         "--port",
//         "8080"
//       )
//       .call(
//         Task.dest,
//         stdout = os.Inherit,
//         stderr = os.Inherit,
//         stdin = os.Inherit
//       )

//     res.exitCode match
//       case 0 => Result.Success("Finished serving")
//       case _ =>
//         Result.Failure(
//           s"Failed to start server. Please check the output above for more details."
//         )
//     end match
//   }

//   def browserSyncConfig: T[PathRef] = Task {
//     val site = live()
//     val file = Task.dest / "bs-config.cjs"

//     val sysS = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT)
//     val sitePath = sysS match
//       case os if os.contains("win") => site.toString.replace("""\""", """\\""")
//       case _                        => site

//     os.write(
//       file,
//       s"""
// /*
//  |--------------------------------------------------------------------------
//  | Browser-sync config file
//  |--------------------------------------------------------------------------
//  |
//  | For up-to-date information about the options:
//  |   http://www.browsersync.io/docs/options/
//  |
//  | There are more options than you see here, these are just the ones that are
//  | set internally. See the website for more info.
//  |
//  |
//  */
// module.exports = {
//     "files": ["$sitePath"],
//     "serveStatic": ["$sitePath"],
//     "watchEvents": [
//         "change"
//     ],
//     "watch": true,
//     "server": true,
// };
// """
//     )
//     PathRef(file)
//   }

//   def mDocLibs = Task {
//     defaultResolver().classpath(mdocDep())
//   }

//   def transitiveDocSources: T[Seq[PathRef]] = Task {
//     // val transitiveDeps = moduleDeps.flatMap(_.moduleDeps).toSet.toSeq
//     // val transitiveAPiSources =
//     //   Task.Traverse(findAllTransitiveDeps.toSeq)(_.docSources)().flatten
//     // if (includeApiDocsFromThisModule) {
//     //   transitiveAPiSources ++ docSources()
//     // } else transitiveAPiSources

//     Task.traverse(findAllTransitiveDeps.toSeq)(_.docSources)().flatten
//   }

//   def mdocSourceDir = Task.Source(mdocDir)

//   def mdocDir = super.moduleDir / "docs"

//   def assetDir = mdocDir / "_assets"

//   def assetDirSource = PathRef(assetDir, true)

//   def compileCpArg: T[Seq[String]] = Task {
//     Seq(
//       "-classpath",
//       compileClasspath().iterator
//         .filter(_.path.ext != "pom")
//         .map(_.path)
//         .mkString(java.io.File.pathSeparator)
//     )
//   }

//   def findPomSettings = Task
//     .traverse(moduleDeps.filter(_.isInstanceOf[PublishModule]))(
//       _ match
//         case pm: PublishModule => pm.pomSettings
//     )
//     .map(_.headOption)

//   /** See https://docs.scala-lang.org/scala3/guides/scaladoc/settings.html
//     *
//     * By default we enable the snippet compiler
//     *
//     * https://docs.scala-lang.org/scala3/guides/scaladoc/snippet-compiler.html
//     *
//     * @return
//     */
//   override def scalaDocOptions = Task {

//     val fromPublishSettings = findPomSettings().map { ps =>
//       val proj = ps.url.split("/").last
//       val allModules = artefactNames()
//         .map(mod =>
//           s""" "${ps.organization}" %% "${mod}" """ // " % "${latestVersion()}" """
//         )
//         .mkString(
//           "libraryDependencies ++= Seq(",
//           ",",
//           ")"
//         )

//       val slink: Seq[String] = ps.versionControl.browsableRepository
//         .map { repo =>
//           Seq(
//             s"-social-links:github::${ps.url}"
//             // "-source-links:github://" ++ repo.replace("https://github.com/","")
//           )
//         }
//         .getOrElse(Seq.empty[String])

//       if artefactNames().isEmpty then slink
//       else
//         Seq("-scastie-configuration", allModules) ++ slink ++ Seq(
//           "-project",
//           proj
//         )
//       end if
//     }
//     // super.scalaDocOptions() ++
//     Seq[String](
//       "-snippet-compiler:compile"
//       // TODO
//       // "-project-version",
//       // latestVersion()
//     ) ++ fromPublishSettings.getOrElse(Seq.empty[String])

//   }

//   /** Creates a static site, with the API docs, and the your own docs.
//     *
//     *   1. Obtain API only docs 2. Obtain your own docs 3. Compare 1 & 2 against caches. Recreate the entire site if the
//     *      API has changed. 4. Delete any removed docs 5. Copy the _contents_ of any changed docs into the site
//     *
//     * This algorithm is potentially a lot more complex than it needs to be.
//     *
//     * However, without 5, live reloading, enabled by third party applications, e.g. browsersync, or VSCodes live reaload
//     * extension, gets janky.
//     *
//     * Or, it takes ages, as generating API docs is slow. Making this, my best take on it.
//     *
//     * @return
//     *   The folder of a static site you can server somwhere. Github pages friendly.
//     */
//   def live: T[os.Path] =
//     Task(persistent = true) { // persistent otherwise live reloading borks
//       mdocSourceDir() // force this to trigger on change to dir sources
//       val apidir = apiOnlyGen()
//       val docdir = docOnlyGen()

//       val cacheDir = Task.dest / "cache"
//       val siteDir = Task.dest / "site"
//       if !os.exists(cacheDir) then os.makeDir.all(cacheDir)
//       end if
//       val apiCacheFile = cacheDir / "cache.txt"
//       val jsCacheFile = cacheDir / "jsCache.txt"
//       val assetCacheFile = cacheDir / "asset.txt"
//       val docCacheFile = cacheDir / "docCache.json"

//       def createDocCache =
//         os.write.over(
//           docCacheFile,
//           upickle.default.write(
//             QuickChange(
//               Seq(),
//               PathRef(apiCacheFile, true),
//               PathRef(apiCacheFile, true)
//             )
//           )
//         )

//       def createAssetCache = os.write.over(assetCacheFile, Array.empty[Byte])

//       if !os.exists(apiCacheFile) then os.write(apiCacheFile, Array.empty[Byte])
//       end if
//       if !os.exists(jsCacheFile) then os.write.over(jsCacheFile, Array.empty[Byte])
//       end if
//       if !os.exists(assetCacheFile) then createAssetCache
//       end if
//       if !os.exists(docCacheFile) then createDocCache
//       end if

//       // API ------
//       val priorApiHash = os.read(apiCacheFile)
//       val apiHash = apidir.sig

//       // If the API has changed, toss everything and start again
//       if priorApiHash != apiHash.toString() then
//         // println("API has changed, regenerating site")
//         os.write.over(cacheDir / "cache.txt", apiHash.toString())
//         os.remove.all(siteDir)
//         os.copy.over(apidir.path, siteDir)

//         // and invalidate the other caches
//         createDocCache
//         createAssetCache
//       end if

//       val refreshStr = s"""
//             <script>
//             const sse = new EventSource("/refresh/v1/sse");
//             sse.addEventListener("message", (e) => {
//             const msg = JSON.parse(e.data);

//             if ("KeepAlive" in msg) console.log("KeepAlive");

//             if ("PageRefresh" in msg) location.reload();
//             });
//             </script>
//             </body>
//             """

//       val htmlFiles =
//         os.walk(siteDir / "docs").filter(_.ext == "html").foreach { htmlFile =>
//           val content = os.read(htmlFile)
//           if !content.contains(
//               """const sse = new EventSource(" / refresh / v1 / sse");"""
//             )
//           then
//             val updatedContent = content.replace(
//               "</body>",
//               refreshStr
//             )
//             os.write.over(htmlFile, updatedContent)
//           end if

//         }

//       val htmlIndex = siteDir / "index.html"
//       if os.exists(htmlIndex) then
//         val content = os.read(htmlIndex)
//         if !content.contains(
//             """const sse = new EventSource(" / refresh / v1 / sse");"""
//           )
//         then
//           val updatedContent = content.replace(
//             "</body>",
//             refreshStr
//           )
//           os.write.over(htmlIndex, updatedContent)
//         end if
//       end if

//       // Docs ------
//       val priorDocHash =
//         upickle.default.read[QuickChange](os.read(docCacheFile))
//       val currDocs =
//         docdir.docs.filter(pr => os.isFile(pr.path))
//       val currDocsRelPaths =
//         currDocs.map(_.path).map(_.subRelativeTo(docdir.base.path)).toSet
//       val priorDocsRelPaths = priorDocHash.docs
//         .map(_.path.subRelativeTo(priorDocHash.base.path))
//         .toSet

//       val allTheDocs = currDocsRelPaths.union(priorDocsRelPaths)

//       // delete removed documents
//       val deletedDocs = currDocsRelPaths.diff(allTheDocs)
//       // // println("to delete" ++ deletedDocs.toString)
//       for aDoc <- deletedDocs do
//         // println("Deleting " + aDoc)
//         os.remove(siteDir / aDoc)
//       end for

//       // create (blank) added documents
//       val newDocs =
//         currDocsRelPaths.diff(priorDocsRelPaths.removedAll(deletedDocs))
//       // println("to add" ++ newDocs.toString)
//       for aDoc <- newDocs do
//         if !os.exists(siteDir / aDoc) then
//           // println("Adding " + aDoc)
//           os.write(siteDir / aDoc, Array.empty[Byte], createFolders = true)
//       end for

//       // Copy contents of changed documents into files
//       val changed = currDocs.map(_.sig).diff(priorDocHash.docs.map(_.sig))

//       val toWrite = currDocs.filter(curDoc => changed.contains(curDoc.sig))

//       toWrite.foreach { aDoc =>
//         val path = aDoc.path.subRelativeTo(docdir.base.path)
//         // println("Writing " + path)
//         // println(siteDir / path)
//         // println(aDoc.path)
//         os.write.over(siteDir / path, os.read(aDoc.path).getBytes())
//       }

//       val mdocProcessedAssets = docdir.staticAssets
//       if os.exists(mdocProcessedAssets.path) then
//         os.copy(
//           mdocProcessedAssets.path,
//           siteDir,
//           mergeFolders = true,
//           replaceExisting = true
//         )
//       end if

//       // val updatedJsDir = docdir.base.path / "js"
//       // if (os.exists(updatedJsDir)) {
//       //   val updatedJsDir_pr = PathRef(updatedJsDir, false)
//       //   val currentJsSources = PathRef(siteDir / "js", false)
//       //   if (!(os.read(jsCacheFile) == updatedJsDir_pr.sig.toString())) {
//       //     println("copy op")
//       //     println(updatedJsDir)
//       //     println(currentJsSources.path)
//       //     os.copy.over(
//       //       updatedJsDir,
//       //       currentJsSources.path
//       //     )
//       //     os.write.over(jsCacheFile, updatedJsDir_pr.sig.toString())
//       //   }
//       // }

//       os.write.over(docCacheFile, upickle.default.write(docdir))
//       siteDir
//     }

//   /** Filthy. String replace stuff to get the paths right.
//     *
//     * Assumes that the mdoc properties file has this in. "js-out-prefix" -> "_assets/js"
//     *
//     * @param docFile
//     */
//   private def fixAssets(docFile: os.Path) =
//     if docFile.ext == "md" then
//       val fixyFixy = os
//         .read(docFile)
//         .replace("../_assets/", "") // Fix pictures etc
//         .replace("""src="_assets/js/""", """src="../js/""") // fix mdoc JS links
//       os.write.over(docFile, fixyFixy.getBytes())

//   def docOnlyGen: T[QuickChange] = Task {
//     val md = mdoc().path
//     val origDocs = mdocSourceDir().path
//     val javadocDir = Task.dest / "javadoc"
//     os.makeDir.all(javadocDir)
//     val combinedStaticDir = Task.dest / "static"
//     os.makeDir.all(combinedStaticDir)

//     // copy mdoccd files in

//     for aDoc <- os.walk(md).filter(os.isFile)
//     do
//       // println(aDoc.ext)
//       aDoc.ext match
//         case "md" =>
//           val rel = (combinedStaticDir / aDoc.subRelativeTo(md))
//           os.copy.over(aDoc, rel, createFolders = true)
//           fixAssets(rel) // pure filth, report as bug?
//         // This deals with mdoc JS integration
//         case "js" =>
//           val name = aDoc.toIO.getName()
//           val rel = aDoc.subRelativeTo(md)

//           val relative = combinedStaticDir / rel
//           // println(s"$aDoc --> $relative ")
//           os.copy.over(
//             aDoc,
//             relative,
//             createFolders = true
//           )
//     end for

//     // copy all other doc files
//     for
//       aDoc <- os.walk(origDocs).filter(os.isFile)
//       rel = (combinedStaticDir / aDoc.subRelativeTo(mdocDir));
//       if !os.exists(rel)
//     do
//       os.copy(aDoc, rel, createFolders = true)
//       fixAssets(rel) // pure filth, report as bug?
//     end for

//     if os.exists(assetDir) then os.copy(assetDir, javadocDir, mergeFolders = true, replaceExisting = true)
//     end if

//     val compileCp = compileCpArg
//     val options = Seq(
//       "-d",
//       javadocDir.toNIO.toString,
//       "-siteroot",
//       combinedStaticDir.toNIO.toString
//     )

//     val localCp = Lib
//       .findSourceFiles(Seq(fakeSource().classes), Seq("tasty"))
//       .map(_.toString()) // fake api to skip potentially slow doc generation

//     jvmWorker()
//       .worker()
//       .docJar(
//         scalaVersion(),
//         scalaOrganization(),
//         scalaDocClasspath(),
//         scalacPluginClasspath(),
//         options ++ compileCpArg() ++ scalaDocOptions()
//           ++ localCp
//       ) match
//       case true =>
//         os.walk(combinedStaticDir / "_docs")
//           .filter(_.ext == "js")
//           .foreach { js =>
//             val rel = js.subRelativeTo(combinedStaticDir / "_docs")
//             // println("processing js file " + js)
//             os.copy.over(js, javadocDir / "docs" / rel)
//           }

//         Result.Success(
//           QuickChange(
//             os.walk(javadocDir / "docs")
//               .filter(os.isFile)
//               .map(PathRef(_))
//               .toSeq,
//             PathRef(javadocDir, true),
//             PathRef(combinedStaticDir / "_assets", true)
//           )
//         )
//       case false =>
//         Result.Failure(
//           s"""Documentation generatation failed. Cause could include be no sources files in : ${sources()} or no doc files in ${docSources()}, or an error message printed above... """
//         )
//     end match
//   }

//   /** Extract the website, from `docJar`
//     *
//     * @return
//     */
//   def publishDocs = Task {
//     val toPublish = docJar().path / os.up / "javadoc"
//     os.copy(toPublish, Task.dest, createFolders = true, replaceExisting = true)
//     // Monkey patch the .js files from mdoc.
//     os.walk(mdoc().path).filter(_.ext == "js").foreach { js =>
//       val rel = js.subRelativeTo(mdoc().path / "_docs")
//       os.copy.over(js, Task.dest / "docs" / rel)
//     }
//     PathRef(Task.dest)
//   }

//   def fakeDoc: T[PathRef] = Task {
//     val emptyDoc = Task.dest / "_docs" / "empty.md"
//     os.makeDir(emptyDoc / os.up)
//     os.write.over(
//       emptyDoc,
//       "# Fake Doc \n \n To trick the API generator into having a link to the docs part of the website"
//         .getBytes()
//     )
//     PathRef(Task.dest)
//   }

//   def fakeSource: T[CompilationResult] = Task {
//     val emptyDoc = Task.dest / "src" / "fake.scala"
//     os.makeDir(emptyDoc / os.up)
//     os.write.over(
//       emptyDoc,
//       "package fake \n \n object Fake: \n  def apply() = ???"
//     )
//     jvmWorker()
//       .worker()
//       .compileMixed(
//         upstreamCompileOutput = upstreamCompileOutput(),
//         sources = Seq(emptyDoc),
//         compileClasspath = compileClasspath().map(_.path),
//         javacOptions = javacOptions(),
//         scalaVersion = scalaVersion(),
//         scalaOrganization = scalaOrganization(),
//         scalacOptions = allScalacOptions(),
//         compilerClasspath = scalaCompilerClasspath(),
//         scalacPluginClasspath = scalacPluginClasspath(),
//         reporter = Task.reporter.apply(hashCode),
//         reportCachedProblems = zincReportCachedProblems(),
//         incrementalCompilation = true,
//         auxiliaryClassFileExtensions = List.empty[String]
//       )
//   }

//   def apiOnlyGen: T[PathRef] = Task {
//     compile()
//     val javadocDir = Task.dest / "javadoc"
//     os.makeDir.all(javadocDir)
//     val combinedStaticDir = Task.dest / "static"
//     os.makeDir.all(combinedStaticDir)

//     val options = Seq(
//       "-d",
//       javadocDir.toNIO.toString,
//       "-siteroot",
//       fakeDoc().path.toNIO.toString,
//       "-Ygenerate-inkuire"
//     )

//     val foundFiles = Lib
//       .findSourceFiles(docSources(), Seq("tasty"))
//       .map(_.toString()) // fake api to skip potentially slow doc generation

//     // println(foundFiles)

//     val docOpts = options ++ compileCpArg() ++ scalaDocOptions() ++ foundFiles

//     jvmWorker()
//       .worker()
//       .docJar(
//         scalaVersion(),
//         scalaOrganization(),
//         scalaDocClasspath(),
//         scalacPluginClasspath(),
//         docOpts
//       ) match
//       case true => Result.Success(PathRef(javadocDir, quick = true))
//       case false =>
//         Result.Failure(
//           s"Documentation generatation failed. This would normally indicate that the standard mill `docJar` command on one of the underlying projects will fail. Please attempt to fix that problem and try again  "
//         )
//     end match
//   }

//   def scalaLibrary: T[Dep] = Task(
//     mvn"org.scala-lang:scala-library:${scalaVersion}"
//   )

//   def pathToImportMap: T[Option[PathRef]] = None

//   def sitePathString: T[String] = Task(publishDocs().toString())

//   /** Overwrites md files which have been pre-processed by mdoc.
//     */
//   override def docResources = Task {
//     val out = super.docResources()
//     for pr <- out do os.copy.over(pr.path, Task.dest)
//     end for
//     os.copy(
//       mdoc().path,
//       Task.dest,
//       mergeFolders = true,
//       replaceExisting = true,
//       createFolders = true
//     )

//     // manually tamper with asset paths!!!
//     for f <- os.walk(Task.dest) do if os.isFile(f) then fixAssets(f)
//     end for

//     Seq(PathRef(Task.dest))
//   }

//   def guessGithubAction: T[String] =
//     s""""
//   buildSite:
//     if: github.event_name != 'pull_request' && github.ref == 'refs/heads/main'
//     needs: build
//     runs-on: ubuntu-latest
//     steps:
//       - uses: actions/setup-java@v4
//         with:
//           java-version: 17
//           distribution: 'temurin'
//       - uses: actions/checkout@v3
//       - run: ./millw site.publishDocs
//       - name: Setup Pages
//         uses: actions/configure-pages@v4
//       - uses: actions/upload-artifact@v3
//         with:
//           name: page
//           path: ${sitePathString()}
//           if-no-files-found: error

//   deploy:
//     needs: site
//     permissions:
//       pages: write
//       id-token: write
//     environment:
//       name: github-pages
//       url: $${{ steps.deployment.outputs.page_url }}
//     runs-on: ubuntu-latest
//     steps:
//     - uses: actions/download-artifact@v3
//       with:
//         name: page
//         path: .
//     - uses: actions/configure-pages@v4
//     - uses: actions/upload-pages-artifact@v2
//       with:
//         path: .
//     - name: Deploy to GitHub Pages
//       id: deployment
//       uses: actions/deploy-pages@v3
// """

//   // This is the source directory, of the entire site.
//   def siteSources = moduleDir / "docs"

//   def mdocSources: T[Seq[PathRef]] = Task {
//     os.walk(mdocDir)
//       .filter(os.isFile)
//       .filter(_.toIO.getName().contains("mdoc.md"))
//       .map(PathRef(_))
//   }

//   def mdoc: Task[PathRef] = Task(persistent = true) {
//     compile()
//     val cacheDir = Task.dest / "cache"
//     val mdoccdDir = Task.dest / "mdoccd"
//     val cacheFile = cacheDir / "cache.json"
//     if !os.exists(cacheDir) then os.makeDir.all(cacheDir)
//     end if
//     if !os.exists(mdoccdDir) then os.makeDir.all(mdoccdDir)
//     end if
//     if !os.exists(cacheFile) then os.write(cacheFile, "[]")
//     end if

//     val cp = runClasspath().map(_.path)
//     val rp = mDocLibs().map(_.path)
//     val dir = Task.dest.toIO.getAbsolutePath
//     val mdocSources_ = mdocSources().filter(pr => os.isFile(pr.path))
//     val cached = upickle.default.read[Seq[PathRef]](os.read(cacheFile))

//     val cachedList =
//       cached.map(cmdoc => cmdoc.path.subRelativeTo(mdocDir)).toSet
//     val currList =
//       mdocSources_.map(cmdoc => cmdoc.path.subRelativeTo(mdocDir)).toSet

//     cachedList.diff(currList).foreach(del => os.remove(mdoccdDir / del))

//     val result = if !mdocSources_.isEmpty then

//       val checkCache = mdocSources_.map(_.sig).diff(cached.map(_.sig))
//       val importMap = pathToImportMap().map(_.path.toIO.getAbsolutePath)
//       println(checkCache)
//       val toProceass = mdocSources_.filter { pr =>
//         checkCache.contains(pr.sig)
//       }
//       val res = if !toProceass.isEmpty then

//         val dirParams = toProceass
//           .map(_.path)
//           .map { pr =>
//             Seq(
//               "--in",
//               pr.toIO.getAbsolutePath,
//               "--out",
//               (mdoccdDir / pr.subRelativeTo(mdocDir)).toIO.getAbsolutePath,
//               "--scalac-options",
//               scalacOptions().mkString(" ")
//             )
//           }
//           .iterator
//           .flatten
//           .toSeq ++ Seq("--classpath", toArgument(cp ++ rp)) ++ importMap.fold(
//           Seq.empty[String]
//         )(i => Seq("--import-map-path", i))
//         Seq("--js-classpath", jsSiteModule.jsclasspath() )

//         val arg1 = Result.create(
//           mill.util.Jvm.callProcess(
//             mainClass = "mdoc.Main",
//             classPath = rp ++ Seq(jsSiteModule.mdocJsProperties().path),
//             jvmArgs = forkArgs(),
//             env = forkEnv(),
//             mainArgs = dirParams,
//             cpPassingJarPath =
//               Some(forkWorkingDir()) // classpath can be long. On windows will barf without passing as Jar
//           )
//         )
//         os.write.over(cacheFile, upickle.default.write(mdocSources_))
//         arg1
//       else Result.Success("No mdoc sources found")

//     if (os.exists(mdoccdDir / "_docs" / "_assets")) {
//       os.remove.all(mdoccdDir / "_assets")
//       os.move(
//         mdoccdDir / "_docs" / "_assets",
//         mdoccdDir / "_assets",
//         replaceExisting = true,
//         createFolders = true
//       )
//     }

//     PathRef(mdoccdDir)
//   }
// end SiteModule
