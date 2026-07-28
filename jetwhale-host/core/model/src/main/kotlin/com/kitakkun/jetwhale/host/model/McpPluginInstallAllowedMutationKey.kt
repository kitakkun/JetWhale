package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

/**
 * Persists whether an AI agent on the MCP server may install official plugins. Off by default:
 * installing a plugin loads new code into the host process, and the MCP port is unauthenticated.
 */
interface McpPluginInstallAllowedMutationKey : MutationKey<Unit, Boolean>
