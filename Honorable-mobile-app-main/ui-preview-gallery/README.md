# UI preview gallery

Open `index.html` in a browser. It works locally without a server. The gallery includes 20 current product screenshots and links to earlier previews.

Regenerate screenshots from the app root with `node scripts/capture-product-previews.cjs`. Capture uses an isolated temporary account ledger and a deterministic search fixture, never live account data.
