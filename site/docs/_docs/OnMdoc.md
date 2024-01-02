# MDoc

When I started this project, I wasn't aware of the existence of the snippet compiler in new scaladoc.

In particular, it's ability to delegate to scastie, along with configure an artefact to be present in the scastie

```scala sc:compile
val x = 1 + 1
println(x)
```
makes new scaladoc a powerful drop in replacement for mdoc. Props to the scala 3 team.

Mdoc is integrated anyway, and may remain relevant - particulaly for corporate environments where scastie will not resove internal dependencies. Or maybe this helps to transition away from a prior solution. It may be worth noting that they play nicely together -

```
```scala mdoc sc:compile
```

Works great in docs.


## Scala JS

I have not yet, managed to configure scalaDocOptions to run scastie in scalaJS mode. MDoc could remain an attractive target for scala JS docs.