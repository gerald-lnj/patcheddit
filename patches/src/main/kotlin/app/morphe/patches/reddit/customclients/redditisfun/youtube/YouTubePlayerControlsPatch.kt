/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 *
 * Ported from MojiRS/revanced-rif-patches
 * (patches/src/main/kotlin/app/revanced/patches/rif/youtube/YouTubePlayerControlsPatch.kt).
 *
 * The upstream patch also depends on its settings screen (`revancedSettingsResourcePatch` +
 * `revancedSettingsPatch`) to add a "YouTube" category with the three caption checkboxes. This
 * port has no settings UI: `Settings.java` (app.morphe.extension.redditisfun) returns the
 * default-on values for `captionsLandscape()`, `captionsVertical()` and `captionsShorts()`, so
 * the resource patch and the settings dependency are dropped here and nothing else changes.
 */

package app.morphe.patches.reddit.customclients.redditisfun.youtube

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.redditisfun.shared.requireScratchRegister
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val EXTENSION = "Lapp/morphe/extension/redditisfun/YouTubePlayerControls;"

// android-youtube-player's LegacyYouTubePlayerView.setCustomPlayerUi(View): rif passes it the
// library's default control overlay once per player. Class and method names are kept by R8 in
// both builds.
private object SetCustomPlayerUiFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type ==
            "Lcom/pierfrancescosoffritti/androidyoutubeplayer/core/player/views/LegacyYouTubePlayerView;" &&
            method.name == "setCustomPlayerUi"
    },
)

// The JS bridge methods the page calls (kept by name: they are @JavascriptInterface). One hook
// each so the controls know the player state without polling.
private fun bridgeFingerprint(name: String) = Fingerprint(
    custom = { method, _ ->
        method.name == name && method.parameterTypes.map { it.toString() } == listOf("Ljava/lang/String;")
    },
)

private val SendStateChangeFingerprint = bridgeFingerprint("sendStateChange")
private val SendErrorFingerprint = bridgeFingerprint("sendError")

val youTubePlayerControlsPatch = bytecodePatch(
    name = "YouTube player controls",
    description = "Double-tap to seek, hold for 2x speed, and speed and captions controls in the YouTube player.",
    default = true,
) {
    category(PatchCategory.MEDIA)
    compatibleWith(*AppCompatibility.RedditIsFun)
    // The RIF extension bundle (Settings.captions*() are the default-on shim values).
    dependsOn(ExtensionPatches.RIF)

    execute {
        // Hook A: after the overlay is attached, add the gestures and the settings button.
        // p0 = the player view, p1 = the overlay.
        val setUi = SetCustomPlayerUiFingerprint.method
        val lastReturn = setUi.instructions.indexOfLast { it.opcode == Opcode.RETURN_VOID }
        if (lastReturn < 0) throw PatchException("return-void not found in setCustomPlayerUi")
        setUi.addInstructions(
            lastReturn,
            "invoke-static { p0, p1 }, $EXTENSION->onPlayerUi(Landroid/view/ViewGroup;Landroid/view/View;)V",
        )

        // Hook C: state and error callbacks from the page; p1 = the state/error string.
        SendStateChangeFingerprint.method.addInstructions(
            0,
            "invoke-static { p1 }, $EXTENSION->onState(Ljava/lang/String;)V",
        )
        SendErrorFingerprint.method.addInstructions(
            0,
            "invoke-static {}, $EXTENSION->onError()V",
        )

        // Hook B: the IFramePlayerOptions builder constructor fills the playerVars JSONObject;
        // hand it over before the constructor returns (v0 is a dead scratch local there).
        val init = YouTubeIframePlayerOptionsBuilderFingerprint.method
        val jsonField = init.instructions.firstNotNullOfOrNull { insn ->
            if (insn.opcode != Opcode.IPUT_OBJECT) return@firstNotNullOfOrNull null
            ((insn as ReferenceInstruction).reference as? FieldReference)
                ?.takeIf { it.type == "Lorg/json/JSONObject;" }
        } ?: throw PatchException("playerVars JSONObject field not found")
        val end = init.instructions.indexOfLast { it.opcode == Opcode.RETURN_VOID }
        if (end < 0) throw PatchException("return-void not found in IFrameOptions builder")
        requireScratchRegister(init)
        init.addInstructions(
            end,
            """
                iget-object v0, p0, $jsonField
                invoke-static { v0 }, $EXTENSION->playerVars(Lorg/json/JSONObject;)V
            """,
        )
    }
}
