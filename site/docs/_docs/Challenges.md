# Challenges

Mill itself, was really nice to work with.

## Mdoc

Getting the classpath _right_ was painful. See here for inspiration:
https://github.com/hmf/mdocMill

Mdoc itself, is also. Well... slow. So we want to take advantage of the caching that mill provides. This was a little dance.

Potential improvement: can we use mdocs watch mode?

## Scaladoc

New scala3doc seems to be a under advertised feature.

https://docs.scala-lang.org/scala3/guides/scaladoc/index.html

Opinion : having your docs and the API docs unified makes life in userland better. I'd love to see more people give this a go.

## Effort

There is a simple version of this plugin, built right into mill itself. Just run `docJar`. However, it's slow. Like... suuuuuuuuper slow.

This plugin attempts something a bit odd, and splits the pipline into "API" (glacial) and "docs" (fast-ish. -ish.). The API is generated once, and cached (assuming it isn't changing on you). The docs are generated on every run and the two things combined in `siteGen`.

In combination with mills caching, the turnaround times are... okay. In combination with VSCodes markdown preview, and https://browsersync.io we can get a (sluggish, but clickless - yeyz!) live preview.

Is this complexity worth it ?

## Publishing

Some jiggery monkery was required to get github actions, to publish the website. The https://github.com/Quafadas/mill_scala3_mdoc_site/blob/main/.github/workflows/build.yml file, contains my best stab. You're reading this, so it does something :-).


