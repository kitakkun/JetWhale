/**
 * Sections that moved to another page, keyed by their old `/<page>#<anchor>`, valued with the new
 * one. A link to an old location — from a release note, an older host or another site — is sent to
 * the section's new home instead of to the top of the old page.
 */
export const movedAnchors: Record<string, string> = {}
