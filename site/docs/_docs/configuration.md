# Goal

Batteries included documentation plugin with sane defaults to document a scala3 library published and built with mill.

## Features

- Recursive module search to generate API doc for all modules which extend `PublishModule`
- Uses Scaladoc for static site generation - combining API doc _and_ your docs together
- Uses mill caching to accelerate the editing loop
- Auto fixes assets paths so you can enjoy IDE previews _and_ seamlessly deploy to github pages
- Plays nicely with modern git actions for ease of deployment to github pages
- Ensures "live reload" works seamlessly through something like the [Live Server Extension](https://marketplace.visualstudio.com/items?itemName=ritwickdey.LiveServer) or [browsersync](https://www.browsersync.io/) works as expected
- Tries hard to set sane scaladoc default flags
  - Project version
  - Github repository link
  - Enable snippet compiler
    -  And configure scastie to include dependancies it finds, so that your example code can be run in browser.

## Examples

From the test suite

https://github.com/Quafadas/millSite/tree/main/itest/src/02-recommended

As a test, I forked PPrint and added this plugin to it. The result is here:
https://quafadas.github.io/PPrint/docs

Small projects:
- https://quafadas.github.io/scautable/docs/
- vexct


## Plugin Configuration

### `def live: T[PathRef]`

Generate the site incrementally. This is the main entry point for the plugin. Given this module in a mill.sc file, where `foo` is a module we wish to document the API for.

```scala
import millSite.SiteModule

object site extends SiteModule {
  // must be scala3+
  override def scalaVersion = T("3.3.1")

  // Will generate an API for all transitive modules
  def moduleDeps = Seq(foo)
}
```
In a terminal, `mill site.live` will generate a static site, including API for `foo`.

On option is to serve the site at the path of this directory, for example using a static webserver. For example, using javas SimpleHttpServer (java 18+)
```$JAVA_HOME/bin/jwebserver -d [[output of mill show site.siteGen]]```

Pro tip - use the -w flag, `mill -w site.live` to regenerate when you save a change to docs**...

** Generating APIs is slow for larger projects. This module splits the API generation into a separate task, so you can edit and view docs without waiting for the API to regenerate.

#### Live reload
Live reload without interacting with the browser is a function of the webserver you use. In VSCode, the [Live Server Extension](https://marketplace.visualstudio.com/items?itemName=ritwickdey.LiveServer) may be worth investigating.

This plugin assumes you're willing to use and builds a little integration with [browsersync](https://www.browsersync.io/).

Install with `npm install -g browser-sync`

`mill site.browserSync` will start a server and open a browser. Any changes to the site will trigger a reload.

Note: You'll sacrifice your terminal to this process - it is recommended to open a second terminal, which runs `mill -w site.live` to enjoy live reloading on edit.

### `def scalaMdocVersion: T[String] = T("2.5.1")`

The version of mdoc (if you use it) to use. 2.5.1 is the latest version at the time of writing. The scaladoc snippet compiler is a drop in replacement for mdoc - you may not need this at all.

### `def scalaDocOptions: T[Seq[String]] = `

By default, enables the snippet compiler and guesses a bunch of other config based on `PublishModule` settings that it finds.

Scaladoc options. See
[scaladoc manual](https://docs.scala-lang.org/scala3/guides/scaladoc/index.html)

By default;

- injects the latest tag into the "projectVersion" variable
- adds a link to the github repo pages configured in publish settings
- Enables the snippet compiler and configures scastie to include dependancies it finds, so that your example code can be run in browser.

### `def guessGithubAction: T[String]`

Proposes a github action workflow, to enable your site in CI. It will likely need tweaking, but should provide a good starting point. You'll need to enable pages on the repository, and set the pages to be updated via GHA.

![GHA](../images/GHA_setup.png)

You may wish to append `/docs` to the standard pages URL, in order to direct users to your carefully crafted documentation.

