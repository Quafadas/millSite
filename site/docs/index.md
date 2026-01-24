# Goal

Batteries included documentation plugin for mill / laika / mdoc.

Aims to provide
- sane defaults to document a scala3 library published and built with mill.
- fast live reload on change


## Getting started

Quickstart `build.mill` to publish a website, for a module `foo`, which extends `PublishModule`


Current version is : `io.github.quafadas:millSite_mill1_3.8:`${version.latest}


```scala
//| mill-version: 1.1.0-RC4
//| mill-jvm-version: 21
//| mvnDeps:
//| - io.github.quafadas:millSite_mill1_3.8:version.latest

import io.github.quafadas.millSite.SiteModule

object foo extends ScalaModule with PublishModule{}

object site extends SiteModule {
  override def scalaVersion = "3.8.1"
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
with which, it shoudl be possible to run in the terminal.

```console
$ mill -w site.serve
```
Changes to index.md should be reloaded-on-change in the browser. Laika will process the markdown and unidoc will generate API docs for `foo` module, and then mill emits a refresh event to the browser.

