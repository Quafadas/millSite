# Challenges

This was a deep rabbit hole.

Mill itself, was really nice to work with. Here's a little bit of a list of things which took some time to get setup, and or / remain open for ideas.


## Scaladoc

New scala3doc seems to be a under advertised and awesome feature.

https://docs.scala-lang.org/scala3/guides/scaladoc/index.html

Opinion : having your docs and the API docs unified makes life in userland better. I'd love to see wide adoption.

Niggle: Current scaladoc creates a static site in the which the default "index.html" shows the API. As a user, I don't want to be hit over the head with the API. I want to see the "Getting Started" page.

Solution: Configure this _outside_ the website itself, and append "/docs" (i.e. depart from the github pages checkbox default) to the pages URL you direct people to.

## Mdoc

Getting the classpath _right_ was painful. See here for inspiration:
https://github.com/hmf/mdocMill

Mdoc itself can be a bit slow. So we take advantage of the caching that mill provides, which makes it "fast enough" for now.

Potential improvement: can we use mdocs watch mode?

## Mermaid

https://mermaid.js.org

Woudl be cool. Doesn't work right now, and likely beyond my ability to influence.

```mermaid
  graph TD;
      A-->B;
      A-->C;
      B-->D;
      C-->D;
```

## Effort

There is a simple version of this plugin, built right into mill itself. Just run `docJar`. However for a non-trivially sized API, it's slow. Like... suuuuuuuuper slow, even on incremental change because it seems to always regen the whole API. Plus live reload always breaks because of mills default "delete and recreate" behaviour.

Solution: Use mills caching to provide incremental file level changes _to contents_ rather than delete and recrete, oh, and and pipe the API doc generation through a different task path. Bonkers... but live reload works and I like live reload.

### Live reload
This plugin attempts something a bit odd, and splits the pipline into "API" (glacially slow) and "docs" (fast-ish. -ish.). The API is generated once, and cached (assuming it isn't changing on you). The docs are generated on every run and the two things combined in `siteGen`.

In combination with mills caching, the turnaround times are pretty good. In combination with VSCodes markdown preview, live reload or something like https://browsersync.io we can get a decent live preview experience.

Complex though... jury is out on this one.

## Publishing

Some jiggery monkery was required to get new github actions pages to publish correctly. Seems to work really well now though.

## Ensuring scalaodc features work

- [x] Let's test a source link. [[UserApi|io.github.quafadas.millSite.UserApi]]
- [x] Scastie (snippet compiler)
- [x] Api Gen
- [x] Github pages
- [x] project version
- [ ] mdoc JS
- [ ] cross platform API?