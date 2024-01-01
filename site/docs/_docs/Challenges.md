# Challenges

Mill itself, was really nice to work with.

## Mdoc

Getting the classpath _right_ was painful. See here for inspiration:
https://github.com/hmf/mdocMill

Mdoc itself can be ... a bit slow. So we want to take advantage of the caching that mill provides. I'm happy with this solution.

Potential improvement: can we use mdocs watch mode?

## Mermaid

Woudl be cool.

```mermaid
  graph TD;
      A-->B;
      A-->C;
      B-->D;
      C-->D;
```

## Scaladoc

New scala3doc seems to be a under advertised feature.

https://docs.scala-lang.org/scala3/guides/scaladoc/index.html

Opinion : having your docs and the API docs unified makes life in userland better. I'd love to see more people use it.

## Effort

There is a simple version of this plugin, built right into mill itself. Just run `docJar`. However, it's slow. Like... suuuuuuuuper slow.

And, no mdoc. And someimes I really like seeing the output next to the code.

This plugin attempts something a bit odd, and splits the pipline into "API" (glacially slow) and "docs" (fast-ish. -ish.). The API is generated once, and cached (assuming it isn't changing on you). The docs are generated on every run and the two things combined in `siteGen`.

In combination with mills caching, the turnaround times are pretty good. In combination with VSCodes markdown preview, live reload or something like https://browsersync.io we can get a (performance live preview experience.

Complex though... jury is out on this one.

## Publishing

Some jiggery monkery was required to get github actions to publish the website. It uses the new github pages action, so should be quite future proof. There are a few examples already of this working seamleslly, that I'm happy with.
