/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.redditisfun.video

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private const val EXTENSION = "Lapp/morphe/extension/redditisfun/VideoLinks;"
private const val VIDEO_MODEL = "Lcom/andrewshu/android/reddit/things/objects/ThreadMediaRedditVideo;"

// rif's Reddit video player-link metadata request builder (free j2.a$b.e0 /
// Platinum k2.a$b.e0): static (Uri playerLink) -> Uri, turning
// reddit.com/link/{id}/video/{mediaId}/player into /api/info.json?id=t3_{id}.
// Used only to locate the loader class; matched by shape + its strings (both builds).
internal object VideoInfoRequestFingerprint : Fingerprint(
    isStatic = true,
    returnType = "Landroid/net/Uri;",
    parameters = listOf("Landroid/net/Uri;"),
    custom = { method, _ ->
        method.implementation?.instructions?.let { insns ->
            val strings = insns.filter { it.opcode == Opcode.CONST_STRING }
                .map { (it as ReferenceInstruction).reference.toString() }
            "info.json" in strings && "video" in strings
        } == true
    },
)

@Suppress("unused")
val fixCommentVideoLinksPatch = bytecodePatch(
    name = "Fix comment video links",
    description = "Makes videos posted in comments play in rif's video player instead of failing " +
        "with \"error retrieving Reddit video metadata\".",
    default = true,
) {
    dependsOn(ExtensionPatches.RIF)
    compatibleWith(*AppCompatibility.RedditIsFun)

    execute {
        // The loader's result handler: (ThreadMediaRedditVideo) -> void, in the same class as
        // the request builder. It shows "error retrieving…" when the result is null; before
        // that, substitute a video built from the link's streams. p0 = loader, p1 = result.
        val loaderClass = VideoInfoRequestFingerprint.classDef
        val onResult = mutableClassDefBy(loaderClass).methods.firstOrNull {
            it.returnType == "V" && it.parameterTypes.size == 1 &&
                it.parameterTypes[0].toString() == VIDEO_MODEL
        } ?: throw PatchException("video loader result handler not found in ${loaderClass.type}")
        onResult.addInstructions(
            0,
            """
                invoke-static { p0, p1 }, $EXTENSION->orStreamFallback(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
                move-result-object p1
                check-cast p1, $VIDEO_MODEL
            """,
        )
    }
}
