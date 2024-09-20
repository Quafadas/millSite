package io.github.quafadas.millSite

import mill._
import mill.scalalib._
import mill.scalajslib._

import mill.api.Result
import mill.util.Jvm.createJar
import mill.api.PathRef
import mill.scalalib.api.CompilationResult
import de.tobiasroeser.mill.vcs.version.VcsVersion
import scala.util.Try
import mill.scalalib.publish.PomSettings
import mill.scalalib.publish.License
import mill.scalalib.publish.VersionControl
import os.SubPath
import ClasspathHelp._

trait SiteModule extends ScalaModule {

  val jsSiteModule: SiteJSModule =
    new SiteJSModule {
      override def scalaVersion: T[String] = "3.3.3"
      override def scalaJSVersion: T[String] = "1.16.0"
    }

  def latestVersion = T {
    VcsVersion.vcsState().lastTag.getOrElse("0.0.0").replace("v", "")
  }

  // def scalaVersion = T("3.3.1")

  /** If we're given module dependancies, then assume we probably don't want to
    * include source files in the doc site, in the published API docs.
    */
  def checkModuleModule: Unit = if (moduleDeps.length == 0)
    throw new Exception("You must provide at least one module dependency")

  /** Finds everything that is going to get published
    *
    * @return
    */
  def findAllTransitiveDeps: Set[JavaModule] = {
    def loop(
        acc: Set[JavaModule],
        current: JavaModule
    ): Set[JavaModule] = {
      val newAcc = acc + current
      val newDeps = current.moduleDeps
        .filter(_.isInstanceOf[PublishModule])
        .filterNot(newAcc.contains(_))
        .toSet
      if (newDeps.isEmpty) newAcc
      else newDeps.foldLeft(newAcc)((acc, dep) => loop(acc, dep))
    }
    moduleDeps.foldLeft(Set[JavaModule]())((acc, dep) => loop(acc, dep))
  }

  override def docSources = T.sources {
    T.traverse(findAllTransitiveDeps.toSeq)(_.docSources)().flatten

  }

  override def compileClasspath = T {
    T.traverse(findAllTransitiveDeps.toSeq)(_.compileClasspath)()
      .flatten ++ super.compileClasspath()
  }

  def artefactNames = T.traverse(findAllTransitiveDeps.toSeq)(_.artifactName)

  def scalaMdocVersion: T[String] = T(Versions.mdocVersion)

  def mdocDep: T[Agg[Dep]] = T(
    Agg(
      ivy"org.scalameta::mdoc-js:${scalaMdocVersion()}",
      ivy"org.scalameta::mdoc:${scalaMdocVersion()}"
        .exclude("org.scala-lang" -> "scala3-compiler_3")
        .exclude("org.scala-lang" -> "scala3-library_3"),
      ivy"org.scala-lang::scala3-compiler:${scalaVersion()}",
      ivy"org.scala-lang::scala3-library:${scalaVersion()}",
      ivy"org.scala-lang::tasty-core:${scalaVersion()}",
      ivy"org.scala-lang.modules::scala-xml:2.1.0"
    )
  )

  def browserSync() = T.command {
    val conf = browserSyncConfig()
    os.proc("browser-sync", "start", "--config", conf.path)
      .call(
        cwd = super.millSourcePath,
        stdin = os.Inherit,
        stdout = os.Inherit,
        stderr = os.Inherit
      )
    "Finished browser-sync"
  }

  def browserSyncConfig: T[PathRef] = T {
    val site = live()
    val file = T.dest / "bs-config.cjs"

    val sysS = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT)
    val sitePath = sysS match {
      case os if os.contains("win") => site.toString.replace("""\""", """\\""")
      case _                        => site
    }

    os.write(
      file,
      s"""
/*
 |--------------------------------------------------------------------------
 | Browser-sync config file
 |--------------------------------------------------------------------------
 |
 | For up-to-date information about the options:
 |   http://www.browsersync.io/docs/options/
 |
 | There are more options than you see here, these are just the ones that are
 | set internally. See the website for more info.
 |
 |
 */
module.exports = {
    "files": ["$sitePath"],
    "serveStatic": ["$sitePath"],
    "watchEvents": [
        "change"
    ],
    "watch": true,
    "server": true,
};
"""
    )
    PathRef(file)
  }

  def mdocDepBound: T[Agg[BoundDep]] =
    mdocDep().map(Lib.depToBoundDep(_, scalaVersion()))

  def mDocLibs = T { resolveDeps(mdocDepBound) }

  // def transitiveDocSources: T[Seq[PathRef]] = T {
  //   // val transitiveDeps = moduleDeps.flatMap(_.moduleDeps).toSet.toSeq
  //   val transitiveAPiSources =
  //     T.traverse(findAllTransitiveDeps.toSeq)(_.docSources)().flatten
  //   if (includeApiDocsFromThisModule) {
  //     transitiveAPiSources ++ docSources()
  //   } else transitiveAPiSources
  // }

  def mdocSourceDir = T.source { mdocDir }

  def mdocDir = super.millSourcePath / "docs"

  def assetDir = mdocDir / "_assets"

  def assetDirSource = PathRef(assetDir, true)

  def compileCpArg: T[Seq[String]] = T {
    Seq(
      "-classpath",
      compileClasspath().iterator
        .filter(_.path.ext != "pom")
        .map(_.path)
        .mkString(java.io.File.pathSeparator)
    )
  }

  def findPomSettings = T
    .traverse(moduleDeps.filter(_.isInstanceOf[PublishModule]))(
      _ match {
        case pm: PublishModule => pm.pomSettings
      }
    )
    .map(_.headOption)

  /** See https://docs.scala-lang.org/scala3/guides/scaladoc/settings.html
    *
    * By default we enable the snippet compiler
    *
    * https://docs.scala-lang.org/scala3/guides/scaladoc/snippet-compiler.html
    *
    * @return
    */
  override def scalaDocOptions = T {

    val fromPublishSettings = findPomSettings().map { ps =>
      val proj = ps.url.split("/").last
      val allModules = artefactNames()
        .map(mod =>
          s""" "${ps.organization}" %% "${mod}" % "${latestVersion()}" """
        )
        .mkString(
          "libraryDependencies ++= Seq(",
          ",",
          ")"
        )

      val slink: Seq[String] = ps.versionControl.browsableRepository
        .map { repo =>
          Seq(
            s"-social-links:github::${ps.url}"
            // "-source-links:github://" ++ repo.replace("https://github.com/","")
          )
        }
        .getOrElse(Seq.empty[String])

      if (artefactNames().isEmpty) {
        slink
      } else
        Seq("-scastie-configuration", allModules) ++ slink ++ Seq(
          "-project",
          proj
        )
    }
    super.scalaDocOptions() ++
      Seq[String](
        "-snippet-compiler:compile",
        "-project-version",
        latestVersion()
      ) ++ fromPublishSettings.getOrElse(Seq.empty[String])

  }

  /** Creates a static site, with the API docs, and the your own docs.
    *
    *   1. Obtain API only docs 2. Obtain your own docs 3. Compare 1 & 2 against
    *      caches. Recreate the entire site if the API has changed. 4. Delete
    *      any removed docs 5. Copy the _contents_ of any changed docs into the
    *      site
    *
    * This algorithm is potentially a lot more complex than it needs to be.
    *
    * However, without 5, live reloading, enabled by third party applications,
    * e.g. browsersync, or VSCodes live reaload extension, gets janky.
    *
    * Or, it takes ages, as generating API docs is slow. Making this, my best
    * take on it.
    *
    * @return
    *   The folder of a static site you can server somwhere. Github pages
    *   friendly.
    */
  def live: T[os.Path] =
    T.persistent { // persistent otherwise live reloading borks
      mdocSourceDir() // force this to trigger on change to dir sources
      val apidir = apiOnlyGen()
      val docdir = docOnlyGen()

      val cacheDir = T.dest / "cache"
      val siteDir = T.dest / "site"
      if (!os.exists(cacheDir)) os.makeDir.all(cacheDir)
      val apiCacheFile = cacheDir / "cache.txt"
      val jsCacheFile = cacheDir / "jsCache.txt"
      val assetCacheFile = cacheDir / "asset.txt"
      val docCacheFile = cacheDir / "docCache.json"

      def createDocCache =
        os.write.over(
          docCacheFile,
          upickle.default.write(QuickChange(Seq(), PathRef(apiCacheFile, true)))
        )

      def createAssetCache = os.write.over(assetCacheFile, Array.empty[Byte])

      if (!os.exists(apiCacheFile)) os.write(apiCacheFile, Array.empty[Byte])
      if (!os.exists(jsCacheFile)) os.write.over(jsCacheFile, Array.empty[Byte])
      if (!os.exists(assetCacheFile)) createAssetCache
      if (!os.exists(docCacheFile)) createDocCache

      // API ------
      val priorApiHash = os.read(apiCacheFile)
      val apiHash = apidir.sig

      // If the API has changed, toss everything and start again
      if (priorApiHash != apiHash.toString()) {
        // println("API has changed, regenerating site")
        os.write.over(cacheDir / "cache.txt", apiHash.toString())
        os.remove.all(siteDir)
        os.copy.over(apidir.path, siteDir)

        // and invalidate the other caches
        createDocCache
        createAssetCache
      }

      // Docs ------
      val priorDocHash =
        upickle.default.read[QuickChange](os.read(docCacheFile))
      val currDocs =
        docdir.docs.filter(pr => os.isFile(pr.path))
      val currDocsRelPaths =
        currDocs.map(_.path).map(_.subRelativeTo(docdir.base.path)).toSet
      val priorDocsRelPaths = priorDocHash.docs
        .map(_.path.subRelativeTo(priorDocHash.base.path))
        .toSet

      val allTheDocs = currDocsRelPaths.union(priorDocsRelPaths)

      // delete removed documents
      val deletedDocs = currDocsRelPaths.diff(allTheDocs)
      // // println("to delete" ++ deletedDocs.toString)
      for (aDoc <- deletedDocs) {
        // println("Deleting " + aDoc)
        os.remove(siteDir / aDoc)
      }

      // create (blank) added documents
      val newDocs =
        currDocsRelPaths.diff(priorDocsRelPaths.removedAll(deletedDocs))
      // println("to add" ++ newDocs.toString)
      for (aDoc <- newDocs) {
        if (!os.exists(siteDir / aDoc)) {
          // println("Adding " + aDoc)
          os.write(siteDir / aDoc, Array.empty[Byte], createFolders = true)
        }
      }

      // Copy contents of changed documents into files
      val changed = currDocs.map(_.sig).diff(priorDocHash.docs.map(_.sig))

      val toWrite = currDocs.filter(curDoc => changed.contains(curDoc.sig))

      toWrite.foreach { aDoc =>
        val path = aDoc.path.subRelativeTo(docdir.base.path)
        // println("Writing " + path)
        // println(siteDir / path)
        // println(aDoc.path)
        os.write.over(siteDir / path, os.read(aDoc.path).getBytes())
      }

      if (os.exists(assetDir)) {
        if (!(os.read(assetCacheFile) == assetDirSource.sig.toString())) {
          os.copy(
            assetDir,
            siteDir,
            mergeFolders = true,
            replaceExisting = true
          )
          os.write.over(assetCacheFile, assetDirSource.sig.toString())
        }
      }

      val updatedJsDir = docdir.base.path / "js"
      if (os.exists(updatedJsDir)) {
        val updatedJsDir_pr = PathRef(updatedJsDir, false)
        val currentJsSources = PathRef(siteDir / "js", false)
        if (!(os.read(jsCacheFile) == updatedJsDir_pr.sig.toString())) {
          println("copy op")
          println(updatedJsDir)
          println(currentJsSources.path)
          os.copy.over(
            updatedJsDir,
            currentJsSources.path
          )
          os.write.over(jsCacheFile, updatedJsDir_pr.sig.toString())
        }
      }

      os.write.over(docCacheFile, upickle.default.write(docdir))
      siteDir
    }

  /** Filthy. String replace stuff to get the paths right.
    *
    * Assumes that the mdoc properties file has this in. "js-out-prefix" ->
    * "_assets/js"
    *
    * @param docFile
    */
  private def fixAssets(docFile: os.Path) = {
    if (docFile.ext == "md") {
      val fixyFixy = os
        .read(docFile)
        .replace("../_assets/", "") // Fix pictures etc
        .replace("""src="_assets/js/""", """src="../js/""") // fix mdoc JS links
      os.write.over(docFile, fixyFixy.getBytes())
    }
  }

  def docOnlyGen: T[QuickChange] = T {
    val md = mdoc().path
    val origDocs = mdocSourceDir().path
    val javadocDir = T.dest / "javadoc"
    os.makeDir.all(javadocDir)
    val combinedStaticDir = T.dest / "static"
    os.makeDir.all(combinedStaticDir)

    // copy mdoccd files in

    for {
      aDoc <- os.walk(md).filter(os.isFile)
    } {
      // println(aDoc.ext)
      aDoc.ext match {
        case "md" =>
          val rel = (combinedStaticDir / aDoc.subRelativeTo(md))
          os.copy.over(aDoc, rel, createFolders = true)
          fixAssets(rel) // pure filth, report as bug?
        // This deals with mdoc JS integration
        case "js" =>
          val name = aDoc.toIO.getName()
          // println(name)
          os.copy.over(
            aDoc,
            combinedStaticDir / "_assets" / "js" / name,
            createFolders = true
          )
      }
    }

    // copy all other doc files
    for {
      aDoc <- os.walk(origDocs).filter(os.isFile)
      rel = (combinedStaticDir / aDoc.subRelativeTo(mdocDir));
      if !os.exists(rel)
    } {
      os.copy(aDoc, rel, createFolders = true)
      fixAssets(rel) // pure filth, report as bug?
    }

    if (os.exists(assetDir)) {
      os.copy(assetDir, javadocDir, mergeFolders = true, replaceExisting = true)
    }

    val compileCp = compileCpArg
    val options = Seq(
      "-d",
      javadocDir.toNIO.toString,
      "-siteroot",
      combinedStaticDir.toNIO.toString
    )

    val localCp = Lib
      .findSourceFiles(Seq(fakeSource().classes), Seq("tasty"))
      .map(_.toString()) // fake api to skip potentially slow doc generation

    zincWorker()
      .worker()
      .docJar(
        scalaVersion(),
        scalaOrganization(),
        scalaDocClasspath(),
        scalacPluginClasspath(),
        options ++ compileCpArg() ++ scalaDocOptions()
          ++ localCp
      ) match {
      case true =>
        Result.Success(
          QuickChange(
            os.walk(javadocDir / "docs")
              .filter(os.isFile)
              .map(PathRef(_))
              .toSeq,
            PathRef(javadocDir, true)
          )
        )
      case false =>
        Result.Failure(
          s"""Documentation generatation failed. Cause could include be no sources files in : ${sources()} or no doc files in ${docSources()}, or an error message printed above... """
        )
    }
  }

  /** Extract the website, from `docJar`
    *
    * @return
    */
  def publishDocs = T {
    val toPublish = docJar().path / os.up / "javadoc"
    os.copy(toPublish, T.dest, createFolders = true, replaceExisting = true)

    PathRef(T.dest)
  }

  def fakeDoc: T[PathRef] = T {
    val emptyDoc = T.dest / "_docs" / "empty.md"
    os.makeDir(emptyDoc / os.up)
    os.write.over(
      emptyDoc,
      "# Fake Doc \n \n To trick the API generator into having a link to the docs part of the website"
        .getBytes()
    )
    PathRef(T.dest)
  }

  def fakeSource: T[CompilationResult] = T {
    val emptyDoc = T.dest / "src" / "fake.scala"
    os.makeDir(emptyDoc / os.up)
    os.write.over(
      emptyDoc,
      "package fake \n \n object Fake: \n  def apply() = ???"
    )
    zincWorker()
      .worker()
      .compileMixed(
        upstreamCompileOutput = upstreamCompileOutput(),
        sources = Seq(emptyDoc),
        compileClasspath = compileClasspath().map(_.path),
        javacOptions = javacOptions(),
        scalaVersion = scalaVersion(),
        scalaOrganization = scalaOrganization(),
        scalacOptions = allScalacOptions(),
        compilerClasspath = scalaCompilerClasspath(),
        scalacPluginClasspath = scalacPluginClasspath(),
        reporter = T.reporter.apply(hashCode),
        reportCachedProblems = zincReportCachedProblems()
      )
  }

  def apiOnlyGen: T[PathRef] = T {
    compile()
    val javadocDir = T.dest / "javadoc"
    os.makeDir.all(javadocDir)
    val combinedStaticDir = T.dest / "static"
    os.makeDir.all(combinedStaticDir)

    val compileCp = compileCpArg
    val options = Seq(
      "-d",
      javadocDir.toNIO.toString,
      "-siteroot",
      fakeDoc().path.toNIO.toString,
      "-Ygenerate-inkuire"
    )

    zincWorker()
      .worker()
      .docJar(
        scalaVersion(),
        scalaOrganization(),
        scalaDocClasspath(),
        scalacPluginClasspath(),
        options ++ compileCpArg() ++ scalaDocOptions()
          ++ Lib
            .findSourceFiles(docSources(), Seq("tasty"))
            .map(_.toString()) // transitive api, i.e. module deps.

      ) match {
      case true => Result.Success(PathRef(javadocDir, quick = true))
      case false =>
        Result.Failure(
          s"Documentation generatation failed. This would normally indicate that the standard mill `docJar` command on one of the underlying projects will fail. Please attempt to fix that problem and try again  "
        )
    }
  }

  def scalaLibrary: T[Dep] = T(
    ivy"org.scala-lang:scala-library:${scalaVersion}"
  )

  def sitePathString: T[String] = T { publishDocs().toString() }

  /** Overwrites md files which have been pre-processed by mdoc.
    */
  override def docResources: Target[Seq[PathRef]] = T {
    val out = super.docResources()
    for (pr <- out) {
      os.copy.over(pr.path, T.dest)
    }
    os.copy(
      mdoc().path,
      T.dest,
      mergeFolders = true,
      replaceExisting = true,
      createFolders = true
    )

    // manually tamper with asset paths!!!
    for (f <- os.walk(T.dest)) {
      if (os.isFile(f)) {
        fixAssets(f)
      }
    }

    Seq(PathRef(T.dest))
  }

  def guessGithubAction: T[String] =
    s""""
  buildSite:
    if: github.event_name != 'pull_request' && github.ref == 'refs/heads/main'
    needs: build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/setup-java@v4
        with:
          java-version: 17
          distribution: 'temurin'
      - uses: actions/checkout@v3
      - run: ./millw site.publishDocs
      - name: Setup Pages
        uses: actions/configure-pages@v4
      - uses: actions/upload-artifact@v3
        with:
          name: page
          path: ${sitePathString()}
          if-no-files-found: error

  deploy:
    needs: site
    permissions:
      pages: write
      id-token: write
    environment:
      name: github-pages
      url: $${{ steps.deployment.outputs.page_url }}
    runs-on: ubuntu-latest
    steps:
    - uses: actions/download-artifact@v3
      with:
        name: page
        path: .
    - uses: actions/configure-pages@v4
    - uses: actions/upload-pages-artifact@v2
      with:
        path: .
    - name: Deploy to GitHub Pages
      id: deployment
      uses: actions/deploy-pages@v3
"""

  // This is the source directory, of the entire site.
  def siteSources: T[Seq[PathRef]] = T.sources { super.millSourcePath / "docs" }

  def mdocSources: T[Seq[PathRef]] = T.sources {
    os.walk(mdocDir)
      .filter(os.isFile)
      .filter(_.toIO.getName().contains("mdoc.md"))
      .map(PathRef(_))
  }

  def mdoc: T[PathRef] = T.persistent {
    compile()
    val cacheDir = T.dest / "cache"
    val mdoccdDir = T.dest / "mdoccd"
    val cacheFile = cacheDir / "cache.json"
    if (!os.exists(cacheDir)) os.makeDir.all(cacheDir)
    if (!os.exists(mdoccdDir)) os.makeDir.all(mdoccdDir)
    if (!os.exists(cacheFile)) os.write(cacheFile, "[]")

    val cp = runClasspath().map(_.path)
    val rp = mDocLibs().map(_.path)
    val dir = T.dest.toIO.getAbsolutePath
    val mdocSources_ = mdocSources().filter(pr => os.isFile(pr.path))
    val cached = upickle.default.read[Seq[PathRef]](os.read(cacheFile))

    val cachedList =
      cached.map(cmdoc => cmdoc.path.subRelativeTo(mdocDir)).toSet
    val currList =
      mdocSources_.map(cmdoc => cmdoc.path.subRelativeTo(mdocDir)).toSet

    cachedList.diff(currList).foreach(del => os.remove(mdoccdDir / del))

    if (!mdocSources_.isEmpty) {

      val checkCache = mdocSources_.map(_.sig).diff(cached.map(_.sig))
      // println(checkCache)
      val toProceass = mdocSources_.filter { pr =>
        checkCache.contains(pr.sig)
      }
      if (!toProceass.isEmpty) {

        val dirParams = toProceass
          .map(_.path)
          .map { pr =>
            Seq(
              "--in",
              pr.toIO.getAbsolutePath,
              "--out",
              (mdoccdDir / pr.subRelativeTo(mdocDir)).toIO.getAbsolutePath,
              "--scalac-options",
              scalacOptions().mkString(" ")
            )
          }
          .iterator
          .flatten
          .toSeq ++ Seq("--classpath", toArgument(cp ++ rp))
        // Seq("--js-classpath", jsSiteModule.jsclasspath() )

        mill.util.Jvm.runSubprocess(
          mainClass = "mdoc.Main",
          classPath = rp ++ Seq(jsSiteModule.mdocJsProperties().path),
          jvmArgs = forkArgs(),
          envArgs = forkEnv(),
          mainArgs = dirParams,
          workingDir = forkWorkingDir(),
          useCpPassingJar = true
        ) // classpath can be long. On windows will barf without passing as Jar
        os.write.over(cacheFile, upickle.default.write(mdocSources_))
      }
    }

    if (os.exists(mdoccdDir / "_docs" / "_assets")) {
      os.remove.all(mdoccdDir / "_assets")
      os.move(
        mdoccdDir / "_docs" / "_assets",
        mdoccdDir / "_assets",
        replaceExisting = true,
        createFolders = true
      )
    }

    PathRef(mdoccdDir)
  }

}
