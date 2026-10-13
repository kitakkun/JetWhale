package com.kitakkun.jetwhale.host.model

/**
 * Plugin directories named on the command line with `--plugin-dir`, loaded on top of the managed
 * plugins directory.
 *
 * Their jars are **not** trust-gated, matching the existing `jetwhale.devPluginsDir` behaviour: the
 * trust registry answers "did the person running this host approve this jar", and typing the
 * directory on the command line is that approval. Their plugins are listed as installed like any
 * other, but the jars are not managed: trusting and revoking operate on the managed directory alone.
 *
 * Empty in normal usage, so production behaviour is unchanged.
 */
@JvmInline
value class AdditionalPluginDirectories(val paths: List<String>)
