/**
 * Sections that moved to another page, keyed by their old `/<page>#<anchor>`, valued with the new
 * one. A link to an old location — from a release note, an older host or another site — is sent to
 * the section's new home instead of to the top of the old page.
 */
export const movedAnchors: Record<string, string> = {
  '/guide/getting-started#logging': '/guide/agent-configuration#logging',
  '/guide/getting-started#stopping-a-session': '/guide/agent-configuration#stopping-a-session',
  '/guide/getting-started#reconnecting': '/guide/agent-configuration#reconnecting',
  '/guide/getting-started#session-metadata': '/guide/agent-configuration#session-metadata',
  '/guide/getting-started#zero-config-host-discovery-recommended-for-physical-devices':
    '/guide/connecting#finding-the-host-on-the-network',
  '/guide/getting-started#what-each-candidate-contributes': '/guide/connecting#endpoints',
  '/guide/getting-started#the-candidate-says-whether-ssl-says-what-to-trust':
    '/guide/connecting#secure-connections-wss',
  '/guide/getting-started#narrowing-discovery': '/guide/connecting#finding-the-host-on-the-network',
  '/guide/getting-started#baking-in-the-build-machine-s-address-no-browse':
    '/guide/connecting#baking-in-the-build-machine-s-address',
  '/guide/getting-started#without-the-gradle-plugin': '/guide/connecting#without-the-gradle-plugin',
  '/guide/getting-started#choosing-the-address': '/guide/connecting#choosing-the-address',
  '/guide/getting-started#what-it-costs-the-build-cache': '/guide/connecting#build-cache',
  '/guide/getting-started#secure-connections-wss': '/guide/connecting#secure-connections-wss',
  '/guide/getting-started#which-one-to-use': '/guide/connecting#which-certificate-to-trust',
  '/guide/getting-started#per-platform-pinning-support': '/guide/connecting#per-platform-pinning',
  '/guide/getting-started#ios-local-network-permission': '/guide/connecting#ios-local-network-permission',
  '/guide/getting-started#published-artifacts': '/reference/artifacts#artifacts',

  '/guide/what-is-jetwhale#session-security-indicator': '/guide/host-window#session-security-indicator',

  '/guide/host-window#the-rest-of-the-drawer': '/guide/host-window#the-sidebar-footer',

  '/guide/mcp-server#discovery-tools': '/reference/mcp-tools#discovery-tools',
  '/guide/mcp-server#plugin-ui-tools': '/reference/mcp-tools#plugin-ui-tools',
  '/guide/mcp-server#parameters': '/reference/mcp-tools#parameters',
  '/guide/mcp-server#host-tools': '/reference/mcp-tools#host-tools',
  '/guide/mcp-server#jetwhale-updatesettings': '/reference/mcp-tools#jetwhale-updatesettings',
  '/guide/mcp-server#jetwhale-navigate': '/reference/mcp-tools#jetwhale-navigate',

  '/guide/compose-semantics-inspector#what-the-tree-contains': '/reference/semantics-tree#roots-and-node-types',
  '/guide/compose-semantics-inspector#android-view-support': '/reference/semantics-tree#android-view-nodes',
  '/guide/compose-semantics-inspector#ios-support': '/reference/semantics-tree#ios-nodes',
  '/guide/compose-semantics-inspector#registering-the-plugin':
    '/guide/compose-semantics-inspector#add-the-agent-to-your-app',
  '/guide/compose-semantics-inspector#installing-a-probe': '/guide/compose-semantics-inspector#install-a-probe',
  '/guide/compose-semantics-inspector#from-the-application-layer-recommended':
    '/guide/compose-semantics-inspector#install-a-probe',
  '/guide/compose-semantics-inspector#from-inside-the-composition': '/guide/compose-semantics-inspector#install-a-probe',
  '/guide/compose-semantics-inspector#on-ios': '/guide/compose-semantics-inspector#install-a-probe',
  '/guide/compose-semantics-inspector#using-the-host-ui': '/guide/compose-semantics-inspector#using-it',
  '/guide/compose-semantics-inspector#can-the-user-operate-it': '/reference/semantics-tree#reachability',
  '/guide/compose-semantics-inspector#can-a-finger-reach-it': '/reference/semantics-tree#reachability',
  '/guide/compose-semantics-inspector#why-not-just-tap-it-and-see': '/reference/semantics-tree#reachability',
  '/guide/compose-semantics-inspector#com-kitakkun-jetwhale-semantics-findnodes':
    '/reference/semantics-tree#com-kitakkun-jetwhale-semantics-findnodes',
  '/guide/compose-semantics-inspector#com-kitakkun-jetwhale-semantics-getnodetree':
    '/reference/semantics-tree#com-kitakkun-jetwhale-semantics-getnodetree',
  '/guide/compose-semantics-inspector#compact-text-output': '/reference/semantics-tree#compact-text-output',
  '/guide/compose-semantics-inspector#com-kitakkun-jetwhale-semantics-nodeat':
    '/reference/semantics-tree#com-kitakkun-jetwhale-semantics-nodeat',
  '/guide/compose-semantics-inspector#com-kitakkun-jetwhale-semantics-performnodeaction':
    '/reference/semantics-tree#com-kitakkun-jetwhale-semantics-performnodeaction',
  '/guide/compose-semantics-inspector#getting-a-node-on-screen':
    '/reference/semantics-tree#com-kitakkun-jetwhale-semantics-performnodeaction',
  '/guide/compose-semantics-inspector#com-kitakkun-jetwhale-semantics-getviewattributes':
    '/reference/semantics-tree#com-kitakkun-jetwhale-semantics-getviewattributes',
  '/guide/compose-semantics-inspector#com-kitakkun-jetwhale-semantics-setviewattribute':
    '/reference/semantics-tree#com-kitakkun-jetwhale-semantics-setviewattribute',
  '/guide/compose-semantics-inspector#why-not-the-cli': '/reference/semantics-tree#measurements',
  '/guide/compose-semantics-inspector#are-the-coordinates-right': '/reference/semantics-tree#measurements',
  '/guide/compose-semantics-inspector#platform-support': '/guide/compose-semantics-inspector#install-a-probe',
  '/guide/compose-semantics-inspector#web': '/reference/semantics-tree#custom-scenes-and-threading',

  '/guide/network-inspector#ktor': '/guide/network-inspector#add-the-agent-to-your-app',
  '/guide/network-inspector#attaching-to-a-client-you-didn-t-build': '/guide/network-inspector#add-the-agent-to-your-app',
  '/guide/network-inspector#okhttp': '/guide/network-inspector#add-the-agent-to-your-app',
  '/guide/network-inspector#body-capture-limit': '/guide/network-inspector#body-capture-limits',
  '/guide/network-inspector#inspecting-traffic': '/guide/network-inspector#traffic',
  '/guide/network-inspector#mocking-responses': '/guide/network-inspector#mocks',
  '/guide/network-inspector#what-a-rule-looks-like': '/guide/network-inspector#mocks',

  '/guide/nav3-navigator#what-you-get-in-the-host': '/guide/nav3-navigator#using-it',
  '/guide/nav3-navigator#what-it-refuses-to-do': '/guide/nav3-navigator#limits',

  '/guide/storage-inspector#adding-your-own-directories-and-stores':
    '/guide/storage-inspector#your-own-directories-and-stores',
  '/guide/storage-inspector#what-you-get-in-the-host': '/guide/storage-inspector#using-it',
  '/guide/storage-inspector#what-it-refuses-to-do': '/guide/storage-inspector#limits',

  '/guide/debug-actions#in-the-host': '/guide/debug-actions#using-it',

  '/guide/device-mirror#what-you-get-in-the-host': '/guide/device-mirror#using-it',
  '/guide/device-mirror#a-physical-iphone-is-view-only': '/guide/device-mirror#limits',
}
