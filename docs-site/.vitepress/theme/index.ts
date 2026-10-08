import { inBrowser, withBase } from 'vitepress'
import type { Router, Theme } from 'vitepress'
import DefaultTheme from 'vitepress/theme'
import { movedAnchors } from './movedAnchors'
import './custom.css'

/** The new location of the section `href` points at, as a base-relative path, or undefined. */
function movedAnchorTarget(href: string): string | undefined {
  const url = new URL(href, location.origin)
  const base = withBase('/')
  let path = url.pathname.startsWith(base) ? `/${url.pathname.slice(base.length)}` : url.pathname
  path = path.replace(/\.html$/, '')
  let hash: string
  try {
    hash = decodeURIComponent(url.hash)
  } catch {
    return undefined
  }
  const target = movedAnchors[`${path}${hash}`]
  if (!target) return undefined
  const [targetPath, targetHash] = target.split('#')
  return `${targetPath}.html#${targetHash}`
}

function followMovedAnchors(router: Router) {
  // The first page is already server-rendered when this runs, so swapping it in place would
  // hydrate the old page's markup with the new page: load the new one instead.
  const initialTarget = movedAnchorTarget(location.href)
  if (initialTarget) {
    location.replace(withBase(initialTarget))
    return
  }
  router.onBeforeRouteChange = (href) => {
    const target = movedAnchorTarget(href)
    if (!target) return
    router.go(withBase(target))
    return false
  }
}

export default {
  extends: DefaultTheme,
  enhanceApp({ router }) {
    if (inBrowser) followMovedAnchors(router)
  },
} satisfies Theme
