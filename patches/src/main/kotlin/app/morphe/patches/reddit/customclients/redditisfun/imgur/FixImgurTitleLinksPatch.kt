/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 * Adapted from notBrian2/revanced-rif-fixes, riffixes/imgur/FixImgurTitleLinksPatch.kt.
 */

package app.morphe.patches.reddit.customclients.redditisfun.imgur

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.PatchCategory
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.redditisfun.shared.method
import app.morphe.patches.reddit.customclients.redditisfun.shared.rifSymbols

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/redditisfun/Imgur;"

@Suppress("unused")
val fixImgurTitleLinksPatch = bytecodePatch(
    name = "Fix imgur title links",
    description = "Fixes imgur album links that include the album title (imgur.com/a/some-title-AbC123) " +
        "failing to load.",
    default = true,
) {
    category(PatchCategory.LINKS)
    compatibleWith(*AppCompatibility.RedditIsFun)
    dependsOn(ExtensionPatches.RIF)

    // "Use public imgur API" replaces the entry of this same builder in execute; finalize runs
    // after every patch's execute, so the id is trimmed before whatever code is at the entry by
    // then (an early return, if that patch was applied).
    //
    // rif's imgur album/gallery API URL builder: static x(String albumId, boolean isGallery) -> Uri.
    finalize {
        val builder = rifSymbols().albumUriBuilder
        method(builder, "x", listOf("Ljava/lang/String;", "Z"), "Landroid/net/Uri;").addInstructions(
            0,
            """
                invoke-static { p0 }, $EXTENSION_CLASS_DESCRIPTOR->albumId(Ljava/lang/String;)Ljava/lang/String;
                move-result-object p0
            """,
        )
    }
}
