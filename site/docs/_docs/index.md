# Goal

Aims to be a batteries included, one stop shop plugin to document a scala3 library published and built with mill.

Quickstart `build.sc` to publish a website, for a module `foo`, which extends `PublishModule`

```
import $ivy.`io.github.quafadas::mill_scala3_site_mdoc::{{projectVersion}}`

import millSite.SiteModule

object foo extends ScalaModule with PublishModule{}

object site extends SiteModule {
  override def scalaVersion = T("3.3.1")
  def moduleDeps = Seq(foo)
}

```
which expects

```
build.sc
foo/
  src/
    package.scala
site/
  docs/
    _docs/
      index.md
```
with which, it shoudl be possible to run

```console
$ mill site.siteGen
```
Serve the static there using javas SimpleHttpServer (java 18+)
```
$JAVA_HOME/bin/jwebserver -d [[output of mill show site.siteGen]]
```

For live reload, other features etc, see Configuration.md