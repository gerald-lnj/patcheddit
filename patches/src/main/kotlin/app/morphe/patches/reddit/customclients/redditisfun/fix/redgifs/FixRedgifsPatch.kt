/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.redditisfun.fix.redgifs

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.fixRedgifsApiPatch
import app.morphe.patches.reddit.customclients.redditisfun.shared.indexOfOnly
import app.morphe.patches.reddit.customclients.redditisfun.shared.method
import app.morphe.patches.reddit.customclients.redditisfun.shared.references
import app.morphe.patches.reddit.customclients.redditisfun.shared.requireScratchRegister
import app.morphe.patches.reddit.customclients.redditisfun.shared.rifSymbols
import com.android.tools.smali.dexlib2.Opcode

internal const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/redditisfun/Redgifs;"

private const val STRING = "Ljava/lang/String;"
private const val FRAGMENT = "Landroidx/fragment/app/Fragment;"

/**
 * Fix Redgifs for rif.
 *
 * Redgifs disabled rif's OAuth client (401 ClientDisabled) and rif pins `api.redgifs.com` to
 * certificates that have since been replaced, so Redgifs videos never load. The token side is the
 * shared [fixRedgifsApiPatch] interceptor, which fetches an anonymous temporary token and answers
 * rif's dead `/v2/oauth/client` login with an emulated OAuth reply — this patch only adds the two
 * hooks the interceptor cannot do:
 *
 *  - skip rif's stale certificate pins, for `api.redgifs.com` only (normal certificate checks stay);
 *  - a Redgifs 404 means the video was deleted: say so and close the viewer instead of falling back
 *    to the browser.
 */
@Suppress("unused")
val fixRedgifsApi = fixRedgifsApiPatch(
    extensionPatch = ExtensionPatches.RIF,
) {
    compatibleWith(*AppCompatibility.RedditIsFun)

    execute {
        val symbols = rifSymbols()

        // region Skip the stale certificate pins of api.redgifs.com.

        // The pin check of the bundled OkHttp that rif's requests are redirected to. Always present:
        // the extension patch this patch depends on is what bundles it.
        val pinChecks = mutableListOf(BundledPinCheckFingerprint.method)

        // Rif's own copy of the pinner, addressed exactly as the ReVanced patch addresses it. It is
        // dead code once the HTTP library redirect has run, so it is only hooked if it is still there.
        nativePinCheckOrNull(symbols.pinFunction)?.let(pinChecks::add)

        for (pinCheck: MutableMethod in pinChecks) {
            requireScratchRegister(pinCheck)
            // OkHttp's CertificatePinner.check(hostname, cleanedPeerCertificatesFn): p1 is the host.
            pinCheck.addInstructionsWithLabels(
                0,
                """
                    invoke-static { p1 }, $EXTENSION_CLASS_DESCRIPTOR->skipPin($STRING)Z
                    move-result v0
                    if-eqz v0, :check_pins
                    return-void
                """,
                ExternalLabel("check_pins", pinCheck.instructions.first()),
            )
        }

        // endregion

        // region Say the video was removed when Redgifs answers 404.

        // The Redgifs video loader's result T(RedgifsItem): a null item means the load failed. Its
        // HTTP status is in redgifs/a.k. p1 is the viewer callback (a Fragment) right before rif's
        // failure call, so a 404 can close the viewer instead of falling through to the browser.
        val videoTask = symbols.videoTask
        val callback = symbols.videoCallback
        val result = method(videoTask, "T", listOf(RIF_REDGIFS_ITEM), "V")

        val fallback = result.indexOfOnly("failure callback") {
            it.opcode == Opcode.INVOKE_INTERFACE && it.references("$callback->${symbols.videoFailed}()V")
        }
        if (!result.instructions[fallback - 1].references("$videoTask->o:$callback")) {
            throw PatchException("$videoTask->T: expected the callback to be loaded right before its failure call")
        }

        result.addInstructionsWithLabels(
            fallback,
            """
                iget v1, p0, $RIF_REDGIFS_STATUS
                const/16 v0, 0x194
                if-ne v1, v0, :not_removed
                instance-of v0, p1, $FRAGMENT
                if-eqz v0, :not_removed
                move-object v0, p1
                check-cast v0, $FRAGMENT
                invoke-virtual { v0 }, $FRAGMENT->${symbols.fragmentActivity}()Landroidx/fragment/app/FragmentActivity;
                move-result-object v0
                invoke-static { v0 }, $EXTENSION_CLASS_DESCRIPTOR->removed(Landroid/app/Activity;)Z
                move-result v0
                if-eqz v0, :not_removed
                return-void
            """,
            ExternalLabel("not_removed", result.instructions[fallback]),
        )

        // endregion
    }
}
