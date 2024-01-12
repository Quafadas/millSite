
## Mermaid

Took me ages to figure out the best thing I could do, is absolutely nothing. [mermaid api](https://mermaid.js.org/config/usage.html#installing-and-hosting-mermaid-on-a-webpage).

We'll got for the ESM import strategy. Just copy and paste this `pre` and `script` combo in any `.md` file, and mermaid bursts into life.

Note: On first load, I see text. On second load (after the artefact is cached i guess) I see a diagram..

```
<pre class="mermaid">
    graph LR
    A --> B
    B --- C[fa:fa-ban forbidden]
    B --- D(fa:fa-spinner);
</pre>

<script type="module">import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.esm.min.mjs';</script>
```


<pre class="mermaid">
    graph LR
    A --> B
    B---C[fa:fa-ban forbidden]
    B---D(fa:fa-spinner);
</pre>

<script type="module">import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.esm.min.mjs';</script>