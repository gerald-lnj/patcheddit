/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 *
 * Ported from MojiRS/revanced-rif-patches
 * (patches/src/main/kotlin/app/revanced/patches/rif/youtube/FixYouTubeVideosPatch.kt).
 */

package app.morphe.patches.reddit.customclients.redditisfun.youtube

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private const val EXTENSION = "Lapp/morphe/extension/redditisfun/YouTubeFix;"
private const val OLD_ORIGIN = "https://www.youtube.com"

private fun constStrings(method: Method) =
    method.implementation?.instructions
        ?.filter { it.opcode == Opcode.CONST_STRING }
        ?.map { (it as ReferenceInstruction).reference.toString() }
        .orEmpty()

// android-youtube-player 12.x IFramePlayerOptions.Builder constructor (free qe.a$a /
// Platinum rd.a$a): sets the default playerVars, including origin = https://www.youtube.com.
// That origin is also the WebView's loadDataWithBaseURL base. Matched by its strings, so
// it works in both builds.
//
// Shared with YouTubePlayerControlsPatch (hook B), which hooks the same constructor.
internal object YouTubeIframePlayerOptionsBuilderFingerprint : Fingerprint(
    name = "<init>",
    custom = { method, _ ->
        constStrings(method).let {
            OLD_ORIGIN in it && "iv_load_policy" in it
        }
    },
)

val fixYouTubeVideosPatch = bytecodePatch(
    name = "Fix YouTube videos",
    description = "Fixes the built-in YouTube player.",
    default = true,
) {
    category(PatchCategory.MEDIA)
    compatibleWith(*AppCompatibility.RedditIsFun)
    // The RIF extension bundle; Settings.context() needs no init hook (see Settings.java).
    dependsOn(ExtensionPatches.RIF)

    execute {
        // YouTube rejects embeds whose origin/Referer isn't https://<app package> (player
        // error 152/153, which the 12.x library reports as "error code 0"). Replace
        // `const-string vX, "https://www.youtube.com"` with the app's origin, same register.
        val method = YouTubeIframePlayerOptionsBuilderFingerprint.method
        val index = method.instructions.indexOfFirst {
            it.opcode == Opcode.CONST_STRING &&
                (it as ReferenceInstruction).reference.toString() == OLD_ORIGIN
        }
        if (index < 0) throw PatchException("YouTube origin string not found")
        val register = (method.instructions[index] as OneRegisterInstruction).registerA
        method.replaceInstruction(index, "invoke-static { }, $EXTENSION->origin()Ljava/lang/String;")
        method.addInstruction(index + 1, "move-result-object v$register")
    }
}
