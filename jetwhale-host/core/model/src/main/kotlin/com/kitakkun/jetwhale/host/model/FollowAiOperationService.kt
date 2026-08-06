package com.kitakkun.jetwhale.host.model

/**
 * Points the main window at whatever plugin an AI agent is currently operating, so a person can
 * watch the agent work instead of reconstructing it from the drawer's activity indicator.
 *
 * The follow only ever moves the main window: a plugin already popped out into its own window is
 * visible as it is, and dragging the main window onto it would take the user off whatever they were
 * looking at for no gain.
 */
interface FollowAiOperationService {
    /**
     * Follows agent operations for as long as this call is active, requesting navigation through
     * [HostNavigationService] as tool calls arrive. Suspends forever; collect exactly once, from the
     * window that owns the navigation, since [HostNavigationService.requests] has a single collector.
     */
    suspend fun followAiOperations()
}
