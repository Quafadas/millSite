# Goal

To be a batteries included, one stop shop plugin to document a scala3 library published and built with mill 1.0+.

## Getting started

Quickstart `build.mill` to publish a website, for a module `foo`, which extends `PublishModule`


Current version is : `io.github.quafadas:millSite_mill1_3.7:`${version.latest}


```scala
//| mill-version: 1.0.3
//| mill-jvm-version: 21
//| mvnDeps:
//| - io.github.quafadas:millSite_mill1_3.7:version.latest

import io.github.quafadas.millSite.SiteModule

object foo extends ScalaModule with PublishModule{}

object site extends SiteModule {
  override def unidocTitle = Task("Mill Site API Documentation")
  def unidocDeps: Seq[JavaModule] = Seq(plugin)


}

```
which expects

```
build.mill
├── foo/
│   └── src/
│       └── foo.scala
└── site/
    ├── docs/
    │   └── index.md
```
with which, it shoudl be possible to run

```console
$ mill -w site.serve
```
Changes to index.md should be reflected in the browser. Mill emits an refresh event via ServerSentEvents which laika is (by default in this plugin) configured to listen for.

### Website Dev
Abbreviated task heirachy.

// TODO plugin diagram


## Motivation

