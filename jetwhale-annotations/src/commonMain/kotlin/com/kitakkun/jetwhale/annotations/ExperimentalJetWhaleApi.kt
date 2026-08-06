package com.kitakkun.jetwhale.annotations

/**
 * Marks an API that may change or be withdrawn in a later release.
 *
 * Distinct from [InternalJetWhaleApi], which is not meant to be called at all. This one is meant to
 * be called — it simply has not settled, so opting in is an acknowledgement that a future upgrade
 * may ask you to change the call.
 *
 * A warning rather than an error: the point is to be noticed, not to stop a build.
 */
@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
public annotation class ExperimentalJetWhaleApi
