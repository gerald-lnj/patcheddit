/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 *
 * Ported from MojiRS/revanced-rif-patches
 * (extensions/extension/src/main/java/app/revanced/extension/rif/YouTubeFix.java).
 */

package app.morphe.extension.redditisfun;

import android.content.Context;

/**
 * Helpers for the "Fix YouTube videos" patch.
 */
public final class YouTubeFix {

    private static final String FALLBACK = "https://www.youtube.com";

    private YouTubeFix() {}

    /**
     * The embed origin for rif's YouTube player (android-youtube-player 12.x), replacing
     * its hard-coded https://www.youtube.com. It is sent as the iframe's origin playerVar
     * and used as the WebView's base URL (so also the Referer). YouTube now rejects embeds
     * unless this is https://{app package}, which the library's 13.0.0 fix also uses.
     */
    public static String origin() {
        try {
            Context ctx = Settings.context();
            if (ctx != null) {
                String pkg = ctx.getPackageName();
                if (pkg != null && !pkg.isEmpty()) return "https://" + pkg;
            }
        } catch (Throwable ignored) {
        }
        return FALLBACK;
    }
}
