# Goal

Aims to be a batteries included, one stop shop plugin to document a scala3 library published and built with mill.

In `build.sc`

```
import $ivy.`io.github.quafadas::mill_scala3_site_mdoc::{{projectVersion}}`

import millSite.SiteModule

object site extends SiteModule {
  override def scalaVersion = T("3.3.1")
  def moduleDeps = Seq(foo)
}

```

```console
$ mill site.siteGen
```


## Features

- Recursive module search to generate API doc for all modules
- Uses Scaladoc (snippet compiler etc) for static site generation - combining API doc _and_ your docs together
- Uses mill caching to accelerate the editing loop
- Auto fixes assets paths so you can both enjoying IDE previews _and_ seamlessly deploy to github pages
- Plays nicely with modern git actions for ease of deployment to github pages
- Undertakes some wild file copy gymnastics, to ensure "live reload" through something like the [Live Server Extension](https://marketplace.visualstudio.com/items?itemName=ritwickdey.LiveServer) or [browsersync](https://www.browsersync.io/) works as expected

## Examples

From the test suite

https://github.com/Quafadas/mill_scala3_mdoc_site/blob/2f67c914124148cd34775a30b1217e1e41fef488/itest/src/02-recommended



## Module properties

### `def siteGen: T[PathRef]`

Generate the site. This is the main entry point for the plugin. Given this module in a mill.sc file, where `foo` is a module we wish to document the API for.

```scala
import millSite.SiteModule

object site extends SiteModule {
  // must be scala3+
  override def scalaVersion = T("3.3.1")

  // Will generate an API for all transitive modules
  def moduleDeps = Seq(foo)
}
```
In a terminal, `mill show site.siteGen` will generate a static site, including API for `foo`.

Serve the site at the path of this directory in a static webserver, to view your shiny new doc site. For example, using javas SimpleHttpServer (java 18+)
```$JAVA_HOME/bin/jwebserver -d [[output of mill show site.siteGen]]```

Pro tip - use the -w flag, `mill -w site.siteGen` to regenerate when you save a change to docs**...

** Generating APIs is slow for larger projects. This module splits the API generation into a separate task, so you can edit and view docs without waiting for the API to regenerate.

#### Live reload
Live reload to view change without browser refresh is a function of the webserver you use, to host the site. In VSCode, the [Live Server Extension](https://marketplace.visualstudio.com/items?itemName=ritwickdey.LiveServer) may be worth investigating.

Otherwise consider something like [browsersync](https://www.browsersync.io/)


### `def scalaMdocVersion: T[String] = T("2.5.1")`

The version of mdoc (if you use it) to use. 2.5.1 is the latest version at the time of writing. The scaladoc snippet compiler is a drop in replacement for mdoc - you may not need this at all.

### `def scalaDocOptions: T[Seq[String]] = `

By default, enable the snippet compiler.
```super.scalaDocOptions() ++ Seq[String]("-snippet-compiler:compile")```

Scaladoc options. See
[scaladoc manual](https://docs.scala-lang.org/scala3/guides/scaladoc/index.html)

If your have mills VCS plugin enabled, something like;

```
  def latestVersion: T[String] = T{VcsVersion.vcsState().lastTag.getOrElse("0.0.0").replace("v", "")}

  override def scalaDocOptions = super.scalaDocOptions() ++  Seq(
    "-scastie-configuration", s"""libraryDependencies += "io.github.quafadas" %% "scautable" % "${latestVersion()}"""",
    "-project", "scautable",
    "-project-version", latestVersion(),
  )
```
Will keep the library version up to date with the latest tag, in the sense that it'll be on the docs website, and

### `def guessGithubAction: T[String]`

Proposes a github action workflow, to enable your site in CI. It will likely need tweaking, but should provide a good starting point. You'll need to enable pages on the repository, and set the pages to be updated via GHA.

![GHA](../images/GHA_setup.png)

