package com.kitakkun.jetwhale.host.architecture

/**
 * Marker for the presenter-role context.
 *
 * A `<Feature>PresenterContext` is a concrete `@Inject` class that aggregates only the
 * dependencies a presenter needs (e.g. MutationKey). A `<Feature>ScreenContext` holds it
 * by composition (has-a), so the presenter requires only [PresenterContext] while the
 * Root requires the full [ScreenContext]. This keeps the presenter's visible dependencies
 * right-sized and enables capability gating of channel consumption.
 */
interface PresenterContext
