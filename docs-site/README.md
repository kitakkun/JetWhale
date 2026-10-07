# Documentation site

The documentation site is built with [VitePress](https://vitepress.dev/) and deployed to GitHub
Pages at <https://kitakkun.github.io/JetWhale/> by `.github/workflows/deploy-docs.yml` on every push
to `main` that touches `docs/` or `docs-site/`.

The split is deliberate:

- **`docs/`** — the Markdown pages and the screenshots they show. Edit pages here.
- **`docs-site/`** — all site tooling (VitePress config and theme, npm packages, static assets,
  version list). The VitePress `srcDir` points at `../docs`.

Adding a new page also requires adding it to the sidebar in `docs-site/.vitepress/config.mts`.

## Screenshots

Screenshots live in `docs/images/<page>/`, one WebP file per theme: `<shot>-light.webp` and
`<shot>-dark.webp`. A page references both by relative path, so a missing file fails the build,
and the theme shows the one that matches the reader's appearance:

```md
![The Network Inspector's traffic list](../images/network-inspector/traffic-light.webp){.light-only}
![The Network Inspector's traffic list](../images/network-inspector/traffic-dark.webp){.dark-only}
```

Keep each file at 250 KB or less: lossy WebP at quality 85–90, or lossless when that is smaller.

## Moving a section

GitHub Pages has no redirects. When a section moves to another page, add its old and new
`/<page>#<anchor>` to `docs-site/.vitepress/theme/movedAnchors.ts`, so links to the old location
still land on it.

## Local development

```shell
cd docs-site
npm install
npm run docs:dev      # live-reload dev server
npm run docs:build    # production build (link checking included)
```

## Versioned docs

The site always serves the **latest** docs (built from `main`) at the root. Older versions are
archived under `https://kitakkun.github.io/JetWhale/<version>/` and selectable from the version
dropdown in the navbar.

`docs-site/versions.json` is the single source of truth: it lists the archived versions, newest
first. Each entry must be the name of a **git tag**; CI checks out that tag and builds its docs
into the `<version>/` sub-path.

### Archiving a version at release time

When releasing (e.g. `1.0.0`), after pushing the release tag:

1. Add the tag name to `docs-site/versions.json` on `main`:

   ```json
   ["1.0.0"]
   ```

2. Push to `main`. CI rebuilds the site: `main`'s docs stay at the root as `latest`, and the
   `1.0.0` docs (frozen at the tag) appear under `/1.0.0/`.

Fixing a typo in an archived version means re-tagging is *not* required for the latest docs — just
fix `main`. Archived versions are immutable snapshots of their tags; only add tags that already
contain a buildable `docs-site/` + `docs/` pair (i.e. tags created after the docs were introduced).
