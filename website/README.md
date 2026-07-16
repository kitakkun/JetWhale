# Documentation site

The documentation site is built with [VitePress](https://vitepress.dev/) and deployed to GitHub
Pages at <https://kitakkun.github.io/JetWhale/> by `.github/workflows/deploy-docs.yml` on every push
to `main` that touches `docs/` or `website/`.

The split is deliberate:

- **`docs/`** — plain Markdown content only. Edit pages here.
- **`website/`** — all site tooling (VitePress config, npm packages, static assets, version list).
  The VitePress `srcDir` points at `../docs`.

Adding a new page also requires adding it to the sidebar in `website/.vitepress/config.mts`.

## Local development

```shell
cd website
npm install
npm run docs:dev      # live-reload dev server
npm run docs:build    # production build (link checking included)
```

## Versioned docs

The site always serves the **latest** docs (built from `main`) at the root. Older versions are
archived under `https://kitakkun.github.io/JetWhale/<version>/` and selectable from the version
dropdown in the navbar.

`website/versions.json` is the single source of truth: it lists the archived versions, newest
first. Each entry must be the name of a **git tag**; CI checks out that tag and builds its docs
into the `<version>/` sub-path.

### Archiving a version at release time

When releasing (e.g. `1.0.0`), after pushing the release tag:

1. Add the tag name to `website/versions.json` on `main`:

   ```json
   ["1.0.0"]
   ```

2. Push to `main`. CI rebuilds the site: `main`'s docs stay at the root as `latest`, and the
   `1.0.0` docs (frozen at the tag) appear under `/1.0.0/`.

Fixing a typo in an archived version means re-tagging is *not* required for the latest docs — just
fix `main`. Archived versions are immutable snapshots of their tags; only add tags that already
contain a buildable `website/` + `docs/` pair (i.e. tags created after the docs were introduced).
