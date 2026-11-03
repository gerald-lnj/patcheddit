/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.redditisfun.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.redditisfun.shared.requireScratchRegister

/*
 * Ported from MojiRS/revanced-rif-patches
 * `patches/src/main/kotlin/app/revanced/patches/rif/ads/DisableAdsPatch.kt`.
 *
 * The upstream settings screen (a "Disable ads" preference category holding the "Block ads"
 * checkbox) is dropped: this bundle's Settings is a default-on shim (PAT settings decision (b)), so
 * the injected guards call `Settings.blockAds()` and it always answers true. Every gate, scratch
 * register check, injected instruction sequence and fingerprint is upstream's; only the imports,
 * the fingerprint declarations (see DisableAdsFingerprints.kt) and the extension class descriptor
 * changed.
 *
 * Free build only: rif is fun golden platinum ships without the ad SDK or the ad code, so the
 * fingerprints have nothing to match there.
 */

/** The extension-side settings holder; `blockAds()` is a `public static boolean`. */
private const val SETTINGS = "Lapp/morphe/extension/redditisfun/Settings;"

@Suppress("unused")
val disableAdsPatch = bytecodePatch(
    name = "Disable ads",
    description = "Removes and does not render all ads.",
) {
    compatibleWith(AppCompatibility.RedditIsFunFree)
    dependsOn(ExtensionPatches.RIF)
    category(PatchCategory.ADS)

    execute {
        // Each gate runs its original logic unless "Block ads" is enabled, in which
        // case it short-circuits (no ad shown / no ad request). v0 is used as scratch,
        // so each method must have a free local (checked, not assumed).
        val booleanGates = listOf(
            FeedAdSlotGateFingerprint,
            AdViewHelperGateFingerprint,
            ImageAlbumGateFingerprint,
        )
        for (fingerprint in booleanGates) {
            val method = fingerprint.method
            requireScratchRegister(method)
            method.addInstructionsWithLabels(
                0,
                """
                    invoke-static {}, $SETTINGS->blockAds()Z
                    move-result v0
                    if-eqz v0, :original
                    const/4 v0, 0x0
                    return v0
                """,
                ExternalLabel("original", method.getInstruction(0)),
            )
        }

        // Album viewer: hand back the plain image adapter so the ad-inserting wrapper is
        // never created (rif's own no-ads path; the helper's other callbacks all check
        // `instanceof` the ad adapter first). p1 = the adapter to wrap.
        val albumAdapterFactory = AlbumAdAdapterFactoryFingerprint.method
        requireScratchRegister(albumAdapterFactory)
        albumAdapterFactory.addInstructionsWithLabels(
            0,
            """
                invoke-static {}, $SETTINGS->blockAds()Z
                move-result v0
                if-eqz v0, :original
                return-object p1
            """,
            ExternalLabel("original", albumAdapterFactory.getInstruction(0)),
        )

        val voidLoaders = listOf(
            NativeAdLoaderFingerprint,
            BannerLoadFingerprint,
            ImageAlbumLoadTriggerFingerprint,
        )
        for (fingerprint in voidLoaders) {
            val method = fingerprint.method
            requireScratchRegister(method)
            method.addInstructionsWithLabels(
                0,
                """
                    invoke-static {}, $SETTINGS->blockAds()Z
                    move-result v0
                    if-eqz v0, :original
                    return-void
                """,
                ExternalLabel("original", method.getInstruction(0)),
            )
        }
    }
}
