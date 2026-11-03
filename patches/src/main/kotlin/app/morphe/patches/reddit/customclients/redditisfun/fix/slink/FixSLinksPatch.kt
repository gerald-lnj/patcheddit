/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.redditisfun.fix.slink

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.RESOLVE_S_LINK_METHOD
import app.morphe.patches.reddit.customclients.fixSLinksPatch
import app.morphe.patches.reddit.customclients.redditisfun.shared.requireScratchRegister

internal const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/redditisfun/FixSLinksPatch;"

/** Called at the top of MainActivity's VIEW-intent dispatcher; rewrites the intent in place. */
internal const val HANDLE_VIEW_INTENT_METHOD = "patchHandleViewIntent(Landroid/app/Activity;)V"

/**
 * Fix share links for rif.
 *
 * Reddit share links (`reddit.com/r/…/s/…`) are resolved to the post they point to before rif's
 * link router sees them, so rif opens them like any other permalink instead of loading the share
 * link in its in-app browser. The resolution itself is the shared [fixSLinksPatch] extension
 * (`app.morphe.extension.redditisfun.FixSLinksPatch`, a subclass of the shared
 * `BaseFixSLinksPatch`); this patch only adds the two hooks that hand links to it:
 *
 *  - the link router, which every link tap inside rif goes through;
 *  - MainActivity's VIEW-intent dispatcher, for links opened from other apps.
 *
 * Rif's `reddit.com/media?url=…` image links are handled by the same extension (the shared base
 * only resolves `/s/` links, so that part is ported from the ReVanced patch).
 */
@Suppress("unused")
val fixSLinksPatch = fixSLinksPatch(
    extensionPatch = ExtensionPatches.RIF,
) {
    compatibleWith(*AppCompatibility.RedditIsFun)

    execute {
        // region Patch the link router.

        // A share link (or media link) is resolved in the background and rif's router is entered
        // again with the permalink, so the tap returns here handled and untouched.
        val router = LinkRouterFingerprint.method
        requireScratchRegister(router)
        router.addInstructionsWithLabels(
            0,
            """
                invoke-static { p0 }, $EXTENSION_CLASS_DESCRIPTOR->$RESOLVE_S_LINK_METHOD
                move-result v0
                if-eqz v0, :not_handled
                return-void
            """,
            ExternalLabel("not_handled", router.instructions.first()),
        )

        // endregion

        // region Patch MainActivity's VIEW-intent dispatcher.

        // Called before the dispatcher reads the intent, which it rewrites in place: it points the
        // placeholder at the subreddit (rif must set up its state; returning early from here broke
        // Back and onSaveInstanceState) and starts the resolution, so the runtime never leaves rif.
        val dispatch = MainActivityIntentFingerprint.method
        dispatch.addInstructions(
            0,
            "invoke-static { p0 }, $EXTENSION_CLASS_DESCRIPTOR->$HANDLE_VIEW_INTENT_METHOD",
        )

        // endregion
    }
}
