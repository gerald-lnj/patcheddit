/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Adapted from notBrian2/revanced-rif-fixes, riffixes/imgur/FixImgurThumbnailsPatch.kt.
 */

package app.morphe.patches.reddit.customclients.redditisfun.imgur

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.util.returnEarly

private const val INTERCEPTOR_CLASS =
    "Lapp/morphe/extension/redditisfun/ImgurProxyInterceptor;"

/**
 * The ReVanced patch injected a URL rewrite into `okhttp3.Request.Builder.url(String)`. This port
 * does the same work in an OkHttp interceptor instead, because:
 *
 *  - rif sends every request through one OkHttp client, and `http/OkHttpRequestHook` already
 *    installs rif interceptors on it, so the interceptor sees every request URL with no
 *    obfuscated `Request$Builder` fingerprint (the reference patch addresses it by its
 *    build-locked obfuscated name `Lokhttp3/c0$a;->x`) and no scratch register.
 *  - the rewrite then also covers the album JSON request itself (which needs the imgur client id)
 *    and the album-cover fallback in the same place, for both builds.
 *
 * [ImgurProxyInterceptor] stays inert until this patch flips its `isPatchIncluded()` to true, so
 * Group 1 can register it unconditionally in `OkHttpRequestHook.getInterceptors()`.
 */
@Suppress("unused")
val fixImgurThumbnailsPatch = bytecodePatch(
    name = "Fix imgur album thumbnails",
    description = "Fixes imgur album thumbnails failing to load: rif fetched them through its own " +
        "imgur proxy, which no longer exists.",
    default = true,
) {
    category(PatchCategory.MEDIA)
    compatibleWith(*AppCompatibility.RedditIsFun)
    dependsOn(ExtensionPatches.RIF)

    execute {
        Fingerprint(
            definingClass = INTERCEPTOR_CLASS,
            name = "isPatchIncluded",
            returnType = "Z",
            parameters = listOf(),
        ).method.returnEarly(true)
    }
}
