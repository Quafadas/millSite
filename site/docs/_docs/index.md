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
    foo.scala
site/
  docs/
    _docs/
      index.md
```
with which, it shoudl be possible to run

```console
$ mill site.publishDocs
```
After which , one can serve the static there using javas SimpleHttpServer (java 18+)

```
$JAVA_HOME/bin/jwebserver -d [[output of mill show site.publishDocs]]
```

So far, so good. But slow.

## Live reload

```console
$ mill -w site.live
```
Will re generate the site every time you save an edit to a doc file. This task, performs an absurd dance, to ensure incremental (fast) updates, enabling "live reload".

See [configuration](configuration.md#live-reload) for more details on getting a good live reload experience.


## Motivation

IMHO, scaladoc is a beautiful paradox.

This plugin sets out 4 different ways to generate scaladoc, depending on your use case.

|               | docJar | publishDocs  | apiOnly  | docOnly  | live |
|---            | ---|--- |---|---|---|
| Fast          |    |    |   | x | x |
| Publishable   |    | x  |   |   |   |
| Live reload   |    |    |   |   | x |
| Asset preview |    | x  |   |   | x |

Plus, some sane default flags for scaladoc.

### docJar

Vanilla mill has a `docJar` task, which generates a jar containing the scaladoc for the module. This is fast, but not publishable

- Out the box, the way scaladoc treats assets (images) it appears one needs to choose between a markdown preview in IDE, or correct publishing.
- Incremental change is _very_ slow, for a non-trivial API, making it awkward to work with.
- You have to put in the headspace, to figure out where the website is and mangle the path yourself... easy but annoying.

### publishDocs
```console
$ mill site.publishDocs
```
A directory you can publish. Simple.

- Fixes asset links
- Source links should work properly

But slow...

### apiGenOnly
```console
$ mill site.apiOnlyGen
```
Skips the docs - if you're doc authoring, the API probably isn't changing - so we use mills cache, to _avoid_ regnerating the API. This can speed up the feedback loop on the docs you are writing (if doc gen is slow).

### docGenOnly
```console
$ mill site.docOnlyGen
```
Doesn't include API generation - this gets us a fast feedback loop to process your actual docs. It janks live realoding though, because of the delete / recreate default behaviour.

Also, as the API doesn't exist, API links won't work properly.

### live
```console
$ mill site.live
```
Publishes _incremental_ content updates by relying on [apiGenOnly](#apigenonly) and [docGenOnly](#docgenonly) to enable live reload.

Downside : links to the API, can't work by construction. They'll look broken here, but the upside is an otherwise great editing experience.
