
## Mermaid

Took me ages to figure out the best thing I could do to enable this, is absolutely nothing. [mermaid api](https://mermaid.js.org/config/usage.html#installing-and-hosting-mermaid-on-a-webpage), as it shoudl work quite well out the box.

We'll use an ESM import strategy.

Create a script "mermaid.js", and place it here

```
site
  └──docs/
      └── _assets/
              ├── js/
                  └── mermaid.js
```

Into mermaid.js,
```js
import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.esm.min.mjs'
```

Now copy and paste this code block any `.md` file, and mermaid bursts into life.

```
<script type="module" src="../js/mermaid.js"></script>
<pre class="mermaid">
    graph LR
    A --> B
    B --- C[fa:fa-ban forbidden]
    B --- D(fa:fa-spinner);
</pre>
```

<script type="module" src="../js/mermaid.js"></script>
<pre class="mermaid">
    graph LR
    Import_Mermaid --> Make_Diagram
    Make_Diagram --> Profit
</pre>
