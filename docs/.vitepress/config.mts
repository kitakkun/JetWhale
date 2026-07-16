import { defineConfig } from 'vitepress'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

// List of archived doc versions (e.g. ["1.0.0"]), built by CI from git tags.
// The dev server and the "latest" build always serve the working-tree docs.
const versions: string[] = JSON.parse(
  readFileSync(fileURLToPath(new URL('../versions.json', import.meta.url)), 'utf-8'),
)

// When CI builds an archived version, it sets DOCS_VERSION so links and the
// version switcher stay correct under the /JetWhale/<version>/ sub-path.
const docsVersion = process.env.DOCS_VERSION // undefined => latest
const base = docsVersion ? `/JetWhale/${docsVersion}/` : '/JetWhale/'

export default defineConfig({
  title: 'JetWhale',
  description:
    'A next-generation, extensible debugging tool for Kotlin and Compose apps',
  base,
  lastUpdated: true,
  head: [
    ['link', { rel: 'icon', type: 'image/svg+xml', href: `${base}icon.svg` }],
    ['link', { rel: 'icon', type: 'image/png', href: `${base}icon.png` }],
  ],
  ignoreDeadLinks: [/^https?:\/\/localhost/],
  themeConfig: {
    logo: '/icon.svg',
    nav: [
      { text: 'Guide', link: '/guide/what-is-jetwhale' },
      { text: 'Plugin Development', link: '/guide/developing-plugins' },
      { text: 'Reference', link: '/reference/protocol' },
      {
        text: docsVersion ?? 'latest',
        items: [
          { text: 'latest', link: 'https://kitakkun.github.io/JetWhale/' },
          ...versions.map((v) => ({
            text: v,
            link: `https://kitakkun.github.io/JetWhale/${v}/`,
          })),
        ],
      },
    ],
    sidebar: [
      {
        text: 'Introduction',
        items: [
          { text: 'What is JetWhale?', link: '/guide/what-is-jetwhale' },
          { text: 'Getting Started', link: '/guide/getting-started' },
        ],
      },
      {
        text: 'Guide',
        items: [
          { text: 'ADB Auto Port Mapping', link: '/guide/adb-auto-port-mapping' },
          { text: 'Network Inspector', link: '/guide/network-inspector' },
          { text: 'MCP Server', link: '/guide/mcp-server' },
          { text: 'Host Settings', link: '/guide/host-settings' },
        ],
      },
      {
        text: 'Plugin Development',
        items: [
          { text: 'Developing Plugins', link: '/guide/developing-plugins' },
        ],
      },
      {
        text: 'Reference',
        items: [{ text: 'Protocol', link: '/reference/protocol' }],
      },
    ],
    socialLinks: [
      { icon: 'github', link: 'https://github.com/kitakkun/JetWhale' },
    ],
    search: { provider: 'local' },
    footer: {
      message: 'Released under the Apache License 2.0.',
      copyright: 'Copyright © kitakkun',
    },
  },
})
