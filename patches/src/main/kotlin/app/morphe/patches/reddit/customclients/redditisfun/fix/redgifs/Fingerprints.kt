/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.redditisfun.fix.redgifs

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.reddit.customclients.http.privateHttpType
import app.morphe.patches.reddit.customclients.http.privateSharedLibraryType
import app.morphe.patches.reddit.customclients.redditisfun.shared.method

/**
 * `okhttp3.CertificatePinner` as it exists inside rif 5.6.22.
 *
 * The obfuscated name is the one thing about this class the shipped HTTP library mapping does
 * state, in both builds and identically:
 *
 * ```
 * okhttp3.CertificatePinner$Builder -> okhttp3.g$a:
 * okhttp3.CertificatePinner -> okhttp3.g:
 * ```
 *
 * (`patches/src/main/resources/redditisfun/{free,donation}/mapping.txt`.) The mapping lists no
 * members for it: its *only* mapped methods are the ones the fixture surface covers, and
 * `CertificatePinner.check` is not among them. The check's obfuscated name and signature therefore
 * come from the ReVanced patch that is proven against both 5.6.22 APKs
 * (notBrian2 `riffixes/redgifs/FixRedgifsPatch.kt:73`, `method("Lokhttp3/g;", "b", …)`), which is
 * corroborated by the OkHttp release rif actually ships (`okhttp/5.0.0-alpha.11`, per the mapping
 * header): in `okhttp-jvm-5.0.0-alpha.11` the one `(String, Function0)V` method of
 * `CertificatePinner` is `check$okhttp`, i.e. `check(String, cleanedPeerCertificatesFn)`.
 */
internal const val OKHTTP_CERTIFICATE_PINNER = "Lokhttp3/g;"

/** `kotlin.jvm.functions.Function0` as it is named inside the bundled HTTP library. */
private val PIN_FUNCTION = privateSharedLibraryType("Lkotlin/jvm/functions/Function0;")

/**
 * The pin check of the OkHttp copy that `HttpClientExtensionPatch` bundles into every client and
 * redirects the app's own `okhttp3.*` references to.
 *
 * This is the copy that actually runs in the patched APK: rif's own obfuscated OkHttp is left
 * untouched (it is "native" in the HTTP library profile) but no longer reachable once the app's
 * references point at the bundled library. Matched by shape — `(String, Function0)V` is unique on
 * `CertificatePinner` — so the name (canonical `check$okhttp`) is not hardcoded here.
 */
internal object BundledPinCheckFingerprint : Fingerprint(
    definingClass = privateHttpType("Lokhttp3/CertificatePinner;"),
    returnType = "V",
    custom = { method, _ ->
        // The bundle may keep kotlin-stdlib's Function0 under either name: prefixed with the rest of
        // the bundled library, or plain if the stdlib itself is not part of the extension.
        val parameters = method.parameterTypes.map { it.toString() }
        parameters.size == 2 && parameters[0] == "Ljava/lang/String;" &&
            parameters[1] in setOf(PIN_FUNCTION, "Lkotlin/jvm/functions/Function0;")
    },
)

/**
 * `CertificatePinner.check(String, Function0)` in rif's own obfuscated OkHttp, or null if this
 * build's `Lokhttp3/g;` has no such member.
 *
 * Addressed exactly as notBrian2 addresses it: class `Lokhttp3/g;`, method `b`,
 * `(String, <build's Function0>)V`. Rif's own pinner is dead code once the HTTP library redirect
 * has run, so a miss here must not fail the patch; the bundled copy above is the one that has to be
 * hooked.
 */
internal fun BytecodePatchContext.nativePinCheckOrNull(pinFunction: String): MutableMethod? {
    val parameters = listOf("Ljava/lang/String;", pinFunction)
    val present = classDefByOrNull(OKHTTP_CERTIFICATE_PINNER)?.methods?.any { method ->
        method.name == "b" && method.returnType == "V" &&
            method.parameterTypes.map { it.toString() } == parameters
    } == true
    return if (present) method(OKHTTP_CERTIFICATE_PINNER, "b", parameters, "V") else null
}

/** rif's Redgifs package: video loader classes, callback interface and HTTP status field. */
internal const val RIF_REDGIFS = "Lcom/andrewshu/android/reddit/browser/gfycat/redgifs"

/** The Redgifs video loader's result item: `RedgifsItem`, null when the load failed. */
internal const val RIF_REDGIFS_ITEM = "$RIF_REDGIFS/RedgifsItem;"

/** `int` field holding the HTTP status of the last Redgifs request. */
internal const val RIF_REDGIFS_STATUS = "$RIF_REDGIFS/a;->k:I"
