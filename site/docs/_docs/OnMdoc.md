# MDoc

When I started this project, I wasn't aware of the existence of the snippet compiler in scala 3 scaladoc.

In particular, it's ability to delegate to scastie, along with configure an artefact to be present in the scastie
```scala
  override def scalaDocOptions = super.scalaDocOptions() ++ Seq(
    "-scastie-configuration", """libraryDependencies += "io.github.quafadas" %% "scautable" % "0.0.5""""
  )

```
makes new scaladoc a powerful drop in replacement for mdoc. Props to the scala 3 team.

Mdoc is integrated anyway, and may remain relevant - particulaly for corporate environments where scastie will not resove internal dependencies.