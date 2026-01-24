package io.github.quafadas.millSite

import mill.*
import mill.scalalib.*
import mill.scalajslib.*
import coursier.maven.MavenRepository
import mill.api.Result
import mill.util.Jvm

// import mill.scalalib.api.CompilationResult
// import de.tobiasroeser.mill.vcs.version.VcsVersion
import scala.util.Try
import scala.util.boundary
import mill.scalalib.publish.PomSettings
import mill.scalalib.publish.License
import mill.scalalib.publish.VersionControl
import os.SubPath
import ClasspathHelp.*
import mill.api.Task.Simple
import mill.api.BuildCtx
import java.net.URLClassLoader

/** A Mill module trait that provides integration with Mdoc for typesafe documentation.
  *
  * Mdoc processes Markdown files containing Scala code blocks, compiling and evaluating the code to ensure
  * documentation examples are correct and up-to-date. Code blocks are executed and their output is inserted into the
  * generated Markdown.
  *
  * ==Features==
  *   - Typesafe documentation with compiled Scala code examples
  *   - Scala.js support for interactive browser examples via [[SiteJSModule]]
  *   - Incremental processing with in-memory caching for fast rebuilds
  *   - Site variable substitution for dynamic content
  *   - Integration with Mill's dependency resolution and classpath management
  *
  * ==Usage==
  * Mix this trait into your Mill build module:
  *
  * {{{n object docs extends MdocModule { override def scalaVersion = "3.3.1" override def moduleDeps = Seq(myLibrary) }
  * }}}
  *
  * ==File Naming Convention==
  * Files ending in `.mdoc.md` are processed by Mdoc. Regular `.md` files are copied as-is to the output directory.
  *
  * @see
  *   [[SiteJSModule]] for Scala.js integration
  * @see
  *   [[https://scalameta.org/mdoc/ Mdoc documentation]]
  */
trait MdocModule extends ScalaModule:

  /** The Scala.js module for interactive code examples in the browser.
    *
    * By default, creates an empty [[SiteJSModule]] that inherits this module's Scala version. Override to provide
    * custom Scala.js dependencies or configuration for interactive documentation examples.
    *
    * If `jsSiteModule.moduleDeps` is non-empty, Scala.js support is enabled and mdoc will process `js` fence modifiers.
    */
  val jsSiteModule: SiteJSModule =
    new SiteJSModule:
      override def scalaVersion: Simple[String] = MdocModule.this.scalaVersion
      override def scalaJSVersion: Simple[String] = Task(Versions.scalaJsVersion)

  /** Finds all transitive publish module dependencies.
    *
    * Recursively traverses `moduleDeps` to find all modules that extend `PublishModule`. This is used to build the
    * complete classpath for mdoc processing, ensuring all library code is available during documentation compilation.
    *
    * @return
    *   Set of all transitive JavaModule dependencies that are publishable
    */
  def findAllTransitiveDeps: Set[JavaModule] =
    def loop(
        acc: Set[JavaModule],
        current: JavaModule
    ): Set[JavaModule] =
      val newAcc = acc + current
      val newDeps = current.moduleDeps
        .filter(_.isInstanceOf[PublishModule])
        .filterNot(newAcc.contains(_))
        .toSet
      if newDeps.isEmpty then newAcc
      else newDeps.foldLeft(newAcc)((acc, dep) => loop(acc, dep))
      end if
    end loop
    moduleDeps.foldLeft(Set[JavaModule]())((acc, dep) => loop(acc, dep))
  end findAllTransitiveDeps

  // override def docSources = Task {
  //   Task.traverse(findAllTransitiveDeps.toSeq)(_.docSources)().flatten
  // }

  /** Compile classpath including all transitive publish module dependencies.
    *
    * Extends the default compile classpath to include classpaths from all modules found by [[findAllTransitiveDeps]],
    * ensuring mdoc can compile code examples that reference any library code.
    *
    * @return
    *   combined classpath from this module and all transitive dependencies
    */
  override def compileClasspath = Task {
    Task.traverse(findAllTransitiveDeps.toSeq)(_.compileClasspath)().flatten
      ++ super.compileClasspath()

  }

  /** The version of Mdoc to use for documentation processing.
    *
    * Defaults to the version specified in [[Versions.mdocVersion]]. Override to use a different Mdoc version.
    *
    * @return
    *   the Mdoc version string
    */
  def scalaMdocVersion: T[String] = Task(Versions.mdocVersion)

  // def scalaMetaDeps = Task {
  //   Seq(
  //     mvn"org.scalameta:common_2.13:4.13.9",
  //     mvn"org.scalameta::scalameta:4.13.9"
  //   )
  // }

  /** Maven dependencies required for Mdoc processing.
    *
    * Adds scalameta-common to the default dependencies, which is required for Mdoc's internal operation.
    *
    * @return
    *   sequence of Maven dependencies
    */
  override def mvnDeps: T[Seq[Dep]] = super.mvnDeps() ++
    Seq(
      mvn"org.scalameta:common_2.13::${Versions.scalaMetaVersion}"
    )

  /** Key-value pairs to substitute in documentation via Mdoc's site variables.
    *
    * These variables can be referenced in Markdown files using `@@VERSION@@` syntax. For example, adding
    * `"VERSION" -> "1.0.0"` allows `@@VERSION@@` in docs to be replaced with `1.0.0`.
    *
    * @return
    *   sequence of (key, value) pairs for variable substitution
    */
  def siteVariables: Task.Simple[Seq[(String, String)]] = Task {
    Seq.empty[(String, String)]
  }

  /** Maven dependencies for the Mdoc runtime.
    *
    * Includes:
    *   - mdoc and mdoc-js for documentation processing
    *   - Scala 3 compiler and library (matched to [[scalaVersion]])
    *   - tasty-core for TASTy file reading
    *   - scala-xml for XML processing
    *
    * Excludes bundled Scala compiler/library to use the version from [[scalaVersion]].
    *
    * @return
    *   sequence of Mdoc runtime dependencies
    */
  def mdocDep: T[Seq[Dep]] = Task(
    Seq(
      mvn"org.scalameta::mdoc-js:${scalaMdocVersion()}",
      mvn"org.scalameta::mdoc:${scalaMdocVersion()}"
        .exclude("org.scala-lang" -> "scala3-compiler_3")
        .exclude("org.scala-lang" -> "scala3-library_3"),
      mvn"org.scala-lang::scala3-compiler:${scalaVersion()}",
      mvn"org.scala-lang::scala3-library:${scalaVersion()}",
      mvn"org.scala-lang::tasty-core:${scalaVersion()}",
      mvn"org.scala-lang.modules::scala-xml:2.1.0"
    )
  )

  /** Resolved classpath for Mdoc runtime dependencies.
    *
    * Resolves [[mdocDep]] using the default resolver to produce the actual JAR files needed to run Mdoc.
    *
    * @return
    *   resolved classpath for Mdoc execution
    */
  def mDocLibs = Task {
    defaultResolver().classpath(mdocDep())
  }

  private def mdocClassPath = Task {
    val jsPropFile =
      if jsSiteModule.moduleDeps.isEmpty then Seq.empty[os.Path]
      else Seq(jsSiteModule.mdocJsProperties().path)

    mDocLibs().map(_.path) ++ jsPropFile
  }

  /** Directory containing the mdoc documentation sources for this module.
    *
    * Defaults to moduleDir/docs and serves as the input root for mdoc processing (e.g., Markdown files, configuration,
    * and related assets). Override to customize where your documentation lives within the module.
    *
    * @return
    *   Path to the docs directory under this module’s directory.
    */
  def mdocDir = super.moduleDir / "docs"

  /** Returns the directory that contains the project's mdoc documentation sources.
    *
    * The directory is exposed as a Mill source input so that changes to any files within it are tracked and invalidate
    * downstream tasks. The returned PathRef points to the directory and enables incremental rebuilds when its contents
    * change.
    *
    * @return
    *   a PathRef to the documentation source directory, tracked as a task input
    */
  def docDir: Simple[PathRef] = Task.Source(mdocDir)

  // def mdocFiles: Task[Seq[PathRef]] = Task {
  //   println("mdoc files")
  //   os.walk(docDir().path)
  //   .filter(_.toString().endsWith("mdoc.md")).map(PathRef(_))
  // }

  // def mdFiles: Task[Seq[PathRef]] = Task {
  //   println("mdFiles")
  //   os.walk(docDir().path).filter(f => !f.toString().endsWith("mdoc.md") && f.toString().endsWith(".md")).map(PathRef(_))
  // }

  // def scalametaCommon = Task {
  //   Seq(
  //     mvn"org.scalameta:common_2.13:4.13.9",
  //     mvn"org.scalameta::scalameta:4.13.9"
  //   )
  // }

  // def scalaMetaCommonLib = Task {
  //   defaultResolver().classpath(scalametaCommon())
  // }

  /** Configures command-line arguments for Mdoc invocation.
    *
    * Builds the argument list including:
    *   - `--in` pointing to [[docDir]]
    *   - `--classpath` with compile and run classpaths
    *   - `--import-map-path` for Scala.js import mapping (if configured)
    *   - `--scalac-options` with [[scalacOptions]] (if non-empty)
    *   - Site variables as `--site.KEY VALUE` pairs from [[siteVariables]]
    *
    * @see
    *   [[https://scalameta.org/mdoc/docs/installation.html#help Mdoc CLI documentation]]
    * @return
    *   sequence of command-line arguments for Mdoc
    */
  def mdocArgs: Task[Seq[String]] = Task {
    val cp = compileClasspath().map(_.path)
    val runCp = runClasspath().map(_.path)
    // val scalametaCommon = scalaMetaCommonLib().map(_.path)
    val siteVars = siteVariables().toSeq.flatMap { case (k, v) => Seq(s"--site.$k", v) }

    val jsArgs = jsSiteModule.moduleDeps.isEmpty match
      case true  => Seq.empty[String]
      case false => Seq("--js-classpath", jsSiteModule.jsclasspath())

    // val toProcess = mdocFiles()
    val importMap =
      if jsSiteModule.scalaJSImportMap().isEmpty then None
      else
        Some(
          jsSiteModule.mdocJsImportMap().path.toIO.getAbsolutePath
        )
    val scalaCOpts = scalacOptions()
    Seq(
      "--in",
      docDir().path.toString()
    )
      ++ Seq("--classpath", toArgument(runCp ++ cp))
      ++ importMap.fold(Seq.empty[String])(i => Seq("--import-map-path", i))
      ++ (if scalaCOpts.nonEmpty then Seq("--scalac-options", scalaCOpts.mkString(" ")) else Seq.empty[String])
      ++ siteVars
    // ++ jsArgs
  }

  private def mdocWorker = Task.Worker {
    val argLogs = mdocEnableArgsLogging()
    val perfLogs = printMetrics()

    if argLogs then println("mdoc: enabled args logging")
    end if
    if perfLogs then println("mdoc: enabled metrics printing")
    end if

    new MdocWorker(mdocClassPath(), forkArgs(), argLogs, perfLogs)
  }

  /** Runs mdoc to generate processed documentation into this task's destination directory.
    *
    * Behavior:
    *   - Ensures sources are compiled and documentation inputs are up to date by invoking `compile()` and `docDir()`.
    *   - Builds CLI arguments from `mdocArgs()`, appending `--out` pointing to `Task.dest`.
    *   - Resolves the mdoc runtime classpath from `mDocLibs()` and invokes `mdoc.Main` in a forked JVM with the
    *     configured JVM arguments (`forkArgs()`) and environment (`forkEnv()`).
    *
    * The resulting documentation is written under `Task.dest` and returned as a `PathRef`.
    *
    * Side effects:
    *   - Writes/overwrites files under this task’s destination directory.
    *
    * Failure conditions:
    *   - Fails if the mdoc process exits non‑zero or if required dependencies are unavailable.
    *
    * @return
    *   a `PathRef` pointing to the directory containing the generated documentation.
    */

  def mdoc2: Task.Simple[PathRef] = Task {
    compile()
    docDir() // force dependency tracking

    val args = mdocArgs() ++ Seq("--out", Task.dest.toString())
    mdocWorker().run(args)
    PathRef(Task.dest)
  }

  /** Returns the number of times the Mdoc worker has been invoked.
    *
    * Useful for testing and diagnostics to verify caching behavior. The counter increments each time Mdoc processes
    * files.
    *
    * @return
    *   the cumulative run count for the Mdoc worker
    */
  def mdocWorkerRunCount: Task[Int] = Task {
    // read the counter from the worker's static stats object
    // This is intentionally simple and for testing/diagnostics only
    MdocWorkerStats.get()
  }

  /** Whether to print timing metrics after each Mdoc run.
    *
    * When enabled, prints detailed performance metrics including:
    *   - Total run time
    *   - Classloader initialization time
    *   - Input scanning and hashing time
    *   - Cache restore/store times
    *   - Mdoc invocation time
    *
    * Defaults to `false`. Override to `true` for performance debugging.
    *
    * @return
    *   true to enable metrics printing
    */
  def printMetrics: Task[Boolean] = Task {
    false
  }

  /** Whether to print the Mdoc command-line arguments for each run.
    *
    * When enabled, prints the full argument list passed to Mdoc. This is verbose and primarily useful for debugging
    * configuration issues.
    *
    * Defaults to `false`. Override to `true` when troubleshooting.
    *
    * @return
    *   true to enable argument logging
    */
  def mdocEnableArgsLogging: Task[Boolean] = Task {
    false
  }

  private object MdocWorkerStats:
    private val counter = new java.util.concurrent.atomic.AtomicInteger(0)
    def inc(): Int = counter.incrementAndGet()
    def get(): Int = counter.get()
  end MdocWorkerStats

  private final class MdocWorker(
      classpath: Seq[os.Path],
      forkArgs: Seq[String],
      initArgLogging: Boolean,
      initPrintMetrics: Boolean
  ) extends AutoCloseable:
    // Lazily initialized classloader and cached reflection objects so repeated runs reuse expensive resources
    // Initial flags are passed from Mill tasks so builds can opt into verbose logging or metrics printing
    private var loader: URLClassLoader = null
    private var mainObj: AnyRef = null
    private var processMethod: java.lang.reflect.Method = null
    // Forward mdoc output to the outer process' stdout (mill's stdout)
    private val stdout: java.io.PrintStream = System.out

    // in-memory cache: hash -> (relPath -> bytes)
    private val memoryCache =
      scala.collection.mutable.Map.empty[String, scala.collection.mutable.Map[String, Array[Byte]]]

    // control printing of per-run arguments (noisy). Store last run args for on-demand inspection.
    private var verboseArgsLogging = false
    private var lastRunArgs: Seq[String] = Seq.empty

    // initialize flags from constructor
    setVerboseArgsLogging(initArgLogging)

    def setVerboseArgsLogging(enabled: Boolean): Unit = this.synchronized {
      verboseArgsLogging = enabled
    }

    private def parseSystemProps: Seq[(String, String)] =
      forkArgs.collect {
        case arg if arg.startsWith("-D") =>
          val kv = arg.drop(2)
          kv.indexOf('=') match
            case -1  => kv -> ""
            case idx => kv.take(idx) -> kv.drop(idx + 1)
          end match
      }

    override def close(): Unit = this.synchronized {
      Option(loader).foreach(_.close())
      loader = null
      mainObj = null
      processMethod = null
      // Do not close System.out
    }

    private def ensureInitialized(): Unit =
      if loader == null then
        loader = Jvm.createClassLoader(classpath)
        mainObj = loader.loadClass("mdoc.Main$").getField("MODULE$").get(null)
        processMethod = mainObj.getClass.getMethod(
          "process",
          classOf[Array[String]],
          classOf[java.io.PrintStream],
          classOf[java.nio.file.Path]
        )
      end if
    end ensureInitialized

    // Warm up classloader and reflective handles at worker creation to avoid paying
    // the startup penalty during the first publish run.
    ensureInitialized()

    private def withSystemProps[T](body: => T): T =
      val props = parseSystemProps
      val originals = props.map { case (k, _) => k -> Option(System.getProperty(k)) }
      props.foreach { case (k, v) => System.setProperty(k, v) }
      try body
      finally
        originals.foreach {
          case (k, Some(v)) => System.setProperty(k, v)
          case (k, None)    => System.clearProperty(k)
        }
      end try
    end withSystemProps

    private def sha1(bytes: Array[Byte]): String =
      val md = java.security.MessageDigest.getInstance("SHA-1")
      md.digest(bytes).map(b => String.format("%02x", Byte.box(b))).mkString
    end sha1

    // restore from in-memory cache
    private def joinPath(base: os.Path, rel: String): os.Path =
      rel.split('/').foldLeft(base)((b, seg) => b / seg)

    private def restoreFromMemCache(hash: String, relPath: String, outDir: os.Path): Unit =
      memoryCache.get(hash).flatMap(_.get(relPath)).foreach { bytes =>
        val dest = joinPath(outDir, relPath)
        os.makeDir.all(dest / os.up)
        os.write.over(dest, bytes)
      }

    // store into in-memory cache
    private def storeToMemCache(hash: String, relPath: String, outDir: os.Path): Unit =
      val src = joinPath(outDir, relPath)
      if os.exists(src) then
        val bytes = os.read.bytes(src)
        val m = memoryCache.getOrElseUpdate(hash, scala.collection.mutable.Map.empty)
        m.update(relPath, bytes)
      end if
    end storeToMemCache

    def run(args: Seq[String]): Unit = this.synchronized {
      // start overall timer for this run
      val runStart = System.nanoTime()

      // measure classloader initialization (ensureInitialized is idempotent)
      val t0 = System.nanoTime()
      ensureInitialized()
      val t1 = System.nanoTime()
      val classloaderInitNanos = t1 - t0

      // reset metrics for the next run (no persistent storage; metrics are local to each run)

      // per-run accumulators
      var restoreNanos = 0L
      var mdocInvocationNanos = 0L
      var storeNanos = 0L
      var fallbackNanos = 0L

      withSystemProps {
        boundary {
          mill.api.ClassLoader.withContextClassLoader(loader) {
            // parse args for --in and --out
            def getFlagValue(flag: String): Option[String] =
              args.sliding(2).collectFirst { case Seq(`flag`, v) => v }

            val inOpt = getFlagValue("--in")
            val outOpt = getFlagValue("--out")

            if inOpt.isEmpty || outOpt.isEmpty then
              // fallback to default behavior
              val exitCode =
                try
                  MdocWorkerStats.inc()
                  processMethod
                    .invoke(
                      mainObj,
                      args.toArray,
                      stdout,
                      java.nio.file.Path.of(os.pwd.toString())
                    )
                    .asInstanceOf[Int]
                catch
                  case ite: java.lang.reflect.InvocationTargetException =>
                    val cause = Option(ite.getCause).getOrElse(ite)
                    throw new RuntimeException(
                      s"mdoc invocation failed: ${cause.getClass.getSimpleName}: ${cause.getMessage}",
                      cause
                    )

              if exitCode != 0 then throw new RuntimeException(s"mdoc failed with exit code $exitCode")
              end if
              boundary.break()
            end if

            val inDir = os.Path(inOpt.get)
            val outDir = os.Path(outOpt.get)

            // gather all input files and time the scan+hash phase (include non-markdown assets)
            val tScanStart = System.nanoTime()
            val allInputFiles = os
              .walk(inDir)
              .filter(os.isFile(_))
              .toSeq

            val inputFiles = allInputFiles.filter { f =>
              val s = f.toString
              s.endsWith(".md") || s.endsWith(".mdoc.md")
            }

            // build config fingerprint (args excluding --in/--out flags and their values and the fork args)
            def stripFlags(flags: Set[String], arr: Seq[String]): Seq[String] =
              val buf = scala.collection.mutable.ArrayBuffer.empty[String]
              var i = 0
              while i < arr.length do
                if flags.contains(arr(i)) && i + 1 < arr.length then i += 2
                else
                  buf += arr(i)
                  i += 1
              end while
              buf.toSeq
            end stripFlags

            val baseArgs = stripFlags(Set("--in", "--out"), args)
            val configBytes = (baseArgs.mkString(" ") + forkArgs.mkString(" ")).getBytes("UTF-8")
            val configHash = sha1(configBytes)

            // compute per-file hashes for all input files (including assets)
            val fileHashes: Map[String, String] = allInputFiles.map { p =>
              val rel = p.relativeTo(inDir).toString()
              val content = os.read.bytes(p)
              val h = sha1(content ++ configHash.getBytes("UTF-8"))
              rel -> h
            }.toMap
            val tScanEnd = System.nanoTime()
            val inputScanNanos = tScanEnd - tScanStart

            // build transient metadata from in-memory cache
            val metadata: Map[String, String] = memoryCache.toSeq.flatMap { case (h, m) =>
              m.keys.map(rel => rel -> h)
            }.toMap

            val (unchanged, changed) = fileHashes.partition { case (rel, h) =>
              memoryCache.get(h).exists(_.contains(rel))
            }

            // ensure output dir exists
            if !os.exists(outDir) then os.makeDir.all(outDir)
            end if

            // restore unchanged files from in-memory cache (timed)
            unchanged.foreach { case (rel, h) =>
              val tRestoreStart = System.nanoTime()
              restoreFromMemCache(h, rel, outDir)
              restoreNanos += (System.nanoTime() - tRestoreStart)
            }

            if changed.isEmpty then
              // nothing to run — outputs restored from in-memory cache
              boundary.break()
            end if

            // run mdoc only on changed files: build per-file --in <file> --out <file> pairs and ensure parent dirs exist
            val filesToProcess = changed.keys.toSeq

            // ensure parent dirs for outputs exist to avoid mdoc errors
            filesToProcess.foreach { rel =>
              val outFile = joinPath(outDir, rel)
              os.makeDir.all(outFile / os.up)
            }

            // Partition changed files into markdown files that need mdoc processing and other files (assets)
            val (mdFilesToProcess, otherFilesToProcess) = filesToProcess.partition { rel =>
              rel.endsWith(".md") || rel.endsWith(".mdoc.md")
            }

            // Copy changed non-markdown files (assets, images, etc.) directly to the output and cache them
            otherFilesToProcess.foreach { rel =>
              val src = joinPath(inDir, rel)
              val dest = joinPath(outDir, rel)
              // parent dirs already created above
              os.copy.over(src, dest)
              // store copied file into in-memory cache
              val h = changed(rel)
              storeToMemCache(h, rel, outDir)
            }

            val pairArgs = mdFilesToProcess.flatMap { rel =>
              val inFile = joinPath(inDir, rel).toString
              val outFile = joinPath(outDir, rel).toString
              Seq("--in", inFile, "--out", outFile)
            }

            val runArgsSeq = baseArgs ++ pairArgs
            // store last args for on-demand inspection; only print if verbose logging is enabled
            lastRunArgs = runArgsSeq
            if verboseArgsLogging then stdout.println(s"mdoc run args: ${runArgsSeq.mkString(" ")}")
            end if
            val runArgs = runArgsSeq.toArray

            try
              MdocWorkerStats.inc()
              val tMdocStart = System.nanoTime()
              val exitCode =
                processMethod
                  .invoke(
                    mainObj,
                    runArgs,
                    stdout,
                    java.nio.file.Path.of(os.pwd.toString())
                  )
                  .asInstanceOf[Int]
              val tMdocEnd = System.nanoTime()
              mdocInvocationNanos += (tMdocEnd - tMdocStart)

              if exitCode != 0 then throw new RuntimeException(s"mdoc failed with exit code $exitCode")
              end if

              // store processed outputs into in-memory cache (timed)
              changed.foreach { case (rel, h) =>
                val tStoreStart = System.nanoTime()
                storeToMemCache(h, rel, outDir)
                storeNanos += (System.nanoTime() - tStoreStart)
              }
            catch
              case e: Throwable =>
                // fallback to full run if per-file run fails (some mdoc versions don't support per-file)
                stdout.println(s"mdoc per-file run failed (${e.getMessage}), falling back to full run")
                val tFallbackStart = System.nanoTime()
                val exitCode =
                  try
                    processMethod
                      .invoke(
                        mainObj,
                        args.toArray,
                        stdout,
                        java.nio.file.Path.of(os.pwd.toString())
                      )
                      .asInstanceOf[Int]
                  catch
                    case ite: java.lang.reflect.InvocationTargetException =>
                      val cause = Option(ite.getCause).getOrElse(ite)
                      throw new RuntimeException(
                        s"mdoc invocation failed: ${cause.getClass.getSimpleName}: ${cause.getMessage}",
                        cause
                      )
                val tFallbackEnd = System.nanoTime()
                fallbackNanos += (tFallbackEnd - tFallbackStart)

                if exitCode != 0 then throw new RuntimeException(s"mdoc failed with exit code $exitCode")
                end if

                // re-scan outputs and update in-memory cache for all files (timed)
                MdocWorkerStats.inc()
                fileHashes.foreach { case (rel, h) =>
                  val tStoreStart = System.nanoTime()
                  storeToMemCache(h, rel, outDir)
                  storeNanos += (System.nanoTime() - tStoreStart)
                }
            end try

            // compute run metrics (local variables)
            val totalNanos = System.nanoTime() - runStart
            // (classloaderInitNanos, inputScanNanos, restoreNanos, mdocInvocationNanos,
            // storeNanos, fallbackNanos) are already local variables updated above
            // counters: inputFiles.length, changed.size, unchanged.size, filesToProcess.size

            // Pretty-print metrics to stdout for quick diagnostics (controlled by flag)
            if initPrintMetrics then
              def nsToMs(n: Long): Double = n.toDouble / 1e6
              stdout.println("mdoc worker last-run metrics:")
              stdout.println(f"  totalNanos: $totalNanos ns (${nsToMs(totalNanos)}%.3f ms)")
              stdout.println(
                f"  classloaderInitNanos: $classloaderInitNanos ns (${nsToMs(classloaderInitNanos)}%.3f ms)"
              )
              stdout.println(f"  inputScanNanos: $inputScanNanos ns (${nsToMs(inputScanNanos)}%.3f ms)")
              stdout.println(f"  restoreNanos: $restoreNanos ns (${nsToMs(restoreNanos)}%.3f ms)")
              stdout.println(f"  mdocInvocationNanos: $mdocInvocationNanos ns (${nsToMs(mdocInvocationNanos)}%.3f ms)")
              stdout.println(f"  storeNanos: $storeNanos ns (${nsToMs(storeNanos)}%.3f ms)")
              stdout.println(f"  fallbackNanos: $fallbackNanos ns (${nsToMs(fallbackNanos)}%.3f ms)")

              // Print concise counters and small file list if helpful
              stdout.println(s"  numInputFiles: ${allInputFiles.length}")
              stdout.println(s"  numChangedFiles: ${changed.size}")
              stdout.println(s"  numUnchangedFiles: ${unchanged.size}")
              stdout.println(s"  filesToProcessCount: ${filesToProcess.size}")
              if filesToProcess.size <= 10 && filesToProcess.nonEmpty then
                stdout.println("  filesToProcess:")
                filesToProcess.foreach(f => stdout.println(s"    - $f"))
              end if
            end if

          }
        }
      }
    }
  end MdocWorker
end MdocModule
