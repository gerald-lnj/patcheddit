/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.redditisfun.ads

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

/*
 * Fingerprints for the "Disable ads" patch, ported from
 * MojiRS/revanced-rif-patches `patches/src/main/kotlin/app/revanced/patches/rif/ads/DisableAdsPatch.kt`.
 *
 * Every condition below is the upstream one, verbatim: the same class/method names, the same
 * `custom` predicates and the same method shapes. Only the fingerprint declaration mechanism was
 * translated (ReVanced's deprecated `fingerprint { custom { … } }` DSL → a plain [Fingerprint]
 * subclass, the form the other rif patches in this bundle use).
 *
 * rif's own ad glue lives in the un-renamed `com.andrewshu.android.reddit.ads` package, so those
 * matches are package/name based rather than obfuscation based; the one gate that lives in the
 * obfuscated helper package (`Le5/…`) is the free build's `e5` package, which is also where the
 * shared table's free-build symbols live (`Le5/g;`, `Le5/u;` in RifSymbols.kt).
 *
 * These are free-build-only fingerprints: golden platinum ships without the ad SDK or the ad code.
 */

/** rif's ad-glue package. Not obfuscated: R8 keeps the ad SDK's wrapper class names. */
private const val ADS_PACKAGE = "Lcom/andrewshu/android/reddit/ads/"

/** Master gate for native feed ads (also triggers the GDPR consent dialog). */
internal object AdViewHelperGateFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "${ADS_PACKAGE}AdViewHelper;" && method.name == "isAdsEnabledAndUnblocked"
    },
)

/** Master gate for image-album (image viewer) ads. */
internal object ImageAlbumGateFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "${ADS_PACKAGE}ImageAlbumAdViewHelper;" &&
            method.name == "isAdsEnabledAndUnblocked"
    },
)

/** Actual loader for native feed/thread ads. */
internal object NativeAdLoaderFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "${ADS_PACKAGE}RifNativeAdLoaderWaitListManager;" && method.name == "loadAds"
    },
)

/** Banner ad loader (header/footer MaxAdView banner). */
internal object BannerLoadFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "${ADS_PACKAGE}BannerAdViewHelper;" && method.name == "loadAd"
    },
)

/** Image-album ad load trigger. */
internal object ImageAlbumLoadTriggerFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "${ADS_PACKAGE}RifAppLovinImageAlbumRecyclerAdapter;" &&
            method.name == "initLoadAdsIfNeeded"
    },
)

/** Feed ad-SLOT gate (free build `e5.e0.d0`); the only override that inserts a NativeAdThreadThing. */
internal object FeedAdSlotGateFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Le5/e0;" && method.name == "d0" && method.returnType == "Z"
    },
)

/**
 * The album viewer fragment's adapter factory (`e2.b.L6`): wraps the image list in
 * RifAppLovinImageAlbumRecyclerAdapter, whose own ad placer loads ads as it binds, so no-op'ing
 * initLoadAdsIfNeeded alone doesn't stop album-viewer ads. Matched as the only method that calls
 * IImageAlbumAdViewHelper.newRecyclerAdapter.
 */
internal object AlbumAdAdapterFactoryFingerprint : Fingerprint(
    custom = { method, _ ->
        method.parameterTypes.size == 1 &&
            method.returnType == "Landroidx/recyclerview/widget/RecyclerView\$h;" &&
            method.implementation?.instructions?.any { insn ->
                insn is ReferenceInstruction &&
                    insn.reference.toString().startsWith(
                        "${ADS_PACKAGE}IImageAlbumAdViewHelper;->newRecyclerAdapter(",
                    )
            } == true
    },
)
