# Challenges

This was a deep rabbit hole.

Mill itself, was really nice to work with. Here's a short of a list of things which took some time to get setup, and or / remain open for ideas.

### Live reload

This is the "killer feature", if it may be described as such.

This plugin splits documentation pipline into "API" (glacially slow) and "docs" (fast-ish. -ish.). The API is generated once, and cached (assuming it isn't changing on you). The docs are generated on every run and the two things combined in `mill -w site.live`.

In combination with mills caching, the turnaround times are pretty good. In combination with VSCodes markdown preview, live reload or something like https://browsersync.io we can get a decent live preview experience.

Complex though... jury is out on this one.

## Scaladoc

New scala3doc seems to be a under advertised and awesome feature.

https://docs.scala-lang.org/scala3/guides/scaladoc/index.html

Opinion : having your docs and the API docs unified makes life in userland better. I'd love to see wide adoption.

Niggle: Current scaladoc creates a static site in the which the default "index.html" shows the API. As a user, I don't want to be hit over the head with the API. I want to see the "Getting Started" page.

![doc_fix](../images/fix_link.png)

Solution: Configure your landing page _outside_ the website itself, and append "/docs" (i.e. depart from the github pages checkbox default) to the pages URL you direct people to.

## Mdoc

Getting the classpath _right_ was painful. See here for inspiration:
https://github.com/hmf/mdocMill

Mdoc itself can be a bit slow. So we take advantage of mill's potential for incremental, persistent caching. Incremental editing, is very fast.

## Mermaid !TODO!

Hopefully via mdoc's scalaJS integration

https://mermaid.js.org

Woudl be cool. Doesn't work right now, and likely beyond my ability to influence.

```mermaid
  graph TD;
      A-->B;
      A-->C;
      B-->D;
      C-->D;
```

<pre class="mermaid">
    graph LR
    A --> B
    B---C[fa:fa-ban forbidden]
    B---D(fa:fa-spinner);
</pre>

<script type="module">import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.esm.min.mjs';</script>

## Complexity

There is a simple version of this plugin, built right into mill itself. Just run `docJar`. However for a non-trivially sized API, it's slow. Like... suuuuuuuuper slow, even on incremental change, because it always re-generates the whole API doc.

Plus live reload always breaks because of mills default "delete and recreate" behaviour.

This plugin accepts a boatload of complexity, to enable an incremental experience.

Importantly though, for publishing, we eject from that complexity and revert to mills own, tested and trusted, `docJar` task. This guarantees reliability, and also reduces the risk of this plugin becoming a maintenance burden.

## Publishing via GHA

Some jiggery monkery was required to get new github actions pages to publish correctly. Seems to work really well now though.

## Todo List

- [x] Let's test a source link. [[UserApi|io.github.quafadas.millSite.UserApi]]
- [x] Scastie (snippet compiler)
- [x] Api Gen
- [x] Github pages
- [x] project version
- [x] mdoc caching
- [ ] mdoc JS
- [ ] cross platform API?