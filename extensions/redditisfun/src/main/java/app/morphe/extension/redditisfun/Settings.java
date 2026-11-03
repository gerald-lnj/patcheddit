/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.redditisfun;

import android.content.Context;

/**
 * Settings for the rif is fun patches. Every feature is always on: this class returns fixed,
 * default-on values and reads no preferences.
 *
 * <p><b>This replaces the ReVanced settings screen (option b).</b> Upstream (MojiRS revanced-rif-patches)
 * exposes these as checkboxes and a slider on a "ReVanced" preference screen
 * ({@code RevancedSettingsFragment} + {@code StepSeekBarPreference}, inheriting rif's
 * {@code RifBaseSettingsFragment}) stored in rif's "settings" SharedPreferences file. This port drops
 * that screen, its fragment, its preference widget and the {@code RifBaseSettingsFragment} stub
 * subproject: the features are unconditional, so the patch layer keeps calling {@code Settings.xxx()}
 * and always gets {@code true}.
 *
 * <p>A future UI would back these with SharedPreferences without changing any caller: keep every
 * method name and default below, and have each getter read
 * {@code context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean(KEY_…, default)}
 * with the {@code KEY_*} constants (the same keys upstream used, so an old settings file keeps
 * working). Defaults therefore must stay the "feature on" values they are here.
 *
 * <p>{@link #init(Context)} and {@link #context()} exist only so patch-layer bytecode and ported
 * extension code that hands over the app Context keep working; nothing here depends on them.
 */
public final class Settings {

    // SharedPreferences keys, kept for the future settings UI (see class comment). Unused today.

    public static final String KEY_BLOCK_ADS = "BLOCK_ADS";
    public static final String KEY_INLINE_IMAGES = "INLINE_IMAGES";
    public static final String KEY_INLINE_IMAGES_SCALE = "INLINE_IMAGES_SCALE";
    public static final String KEY_INLINE_ALBUM_NAVIGATION = "INLINE_ALBUM_NAVIGATION";
    public static final String KEY_INLINE_IMAGES_LONG_PRESS_SELECT = "INLINE_IMAGES_LONG_PRESS_SELECT";
    public static final String KEY_INLINE_IMAGES_LONG_PRESS_DELAY = "INLINE_IMAGES_LONG_PRESS_DELAY";
    public static final String KEY_INLINE_VIDEOS = "INLINE_VIDEOS";
    public static final String KEY_INLINE_VIDEOS_AUTOPLAY = "INLINE_VIDEOS_AUTOPLAY";
    public static final String KEY_YOUTUBE_CAPTIONS_LANDSCAPE = "YOUTUBE_CAPTIONS_LANDSCAPE";
    public static final String KEY_YOUTUBE_CAPTIONS_VERTICAL = "YOUTUBE_CAPTIONS_VERTICAL";
    public static final String KEY_YOUTUBE_CAPTIONS_SHORTS = "YOUTUBE_CAPTIONS_SHORTS";

    /** Long press delay used for inline images, in ms (upstream slider default: 250, range 100-1000). */
    private static final int LONG_PRESS_DELAY_MS = 250;

    private Settings() {}

    /**
     * No-op: the patch layer may still inject a call into rif's Application.onCreate, but no Context
     * is needed when every setting is a constant. Kept so the injected smali call verifies.
     */
    public static void init(Context context) {
        // Intentionally empty. A preferences-backed implementation would capture the application
        // Context here (upstream: appContext = context.getApplicationContext()).
    }

    /**
     * The app Context, resolved through ActivityThread since {@link #init} stores nothing.
     *
     * <p>Ported extension code (inline images/loader) calls this where it needs a Context. Returns
     * null if the hidden API is unavailable; callers must cope with that (upstream did too).
     */
    public static Context context() {
        try {
            return (Context) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Disable ads (free build only: the ads hooks are gated on this). */
    public static boolean blockAds() {
        return true;
    }

    /** Render comment and selftext images inline. */
    public static boolean inlineImages() {
        return true;
    }

    /** Render comment and selftext GIFs/videos inline (only meaningful with {@link #inlineImages()}). */
    public static boolean inlineVideos() {
        return inlineImages();
    }

    /** Start inline videos automatically instead of waiting for a tap. */
    public static boolean autoplayInlineVideos() {
        return true;
    }

    /** Scale inline images to the comment width instead of their native size. */
    public static boolean scaleInlineImages() {
        return true;
    }

    /** Open imgur albums inside the comment view instead of an external browser/album activity. */
    public static boolean inlineAlbumNavigation() {
        return true;
    }

    /** Long pressing an inline image selects its comment (instead of the image's own long-press action). */
    public static boolean longPressSelectComment() {
        return true;
    }

    /** Upstream name of {@link #longPressSelectComment()}, kept for ported call sites. */
    public static boolean longPressImageSelectsComment() {
        return longPressSelectComment();
    }

    /** Hold time in ms before a long press on an inline image counts. */
    public static int longPressDelayMs() {
        return LONG_PRESS_DELAY_MS;
    }

    /** Upstream name of {@link #longPressDelayMs()}, kept for ported call sites. */
    public static int longPressImageDelayMs() {
        return longPressDelayMs();
    }

    /** Master switch for the YouTube player controls (double-tap seek, hold for 2x, speed/captions). */
    public static boolean youTubePlayerControls() {
        return true;
    }

    /**
     * Master switch for YouTube captions; the per-shape getters below all follow it.
     * Off, matching the upstream ReVanced default (its three caption checkboxes default
     * to unchecked). With no settings UI in this port it cannot be turned on; a future
     * preferences screen would back this with SharedPreferences.
     */
    public static boolean captions() {
        return false;
    }

    /** Captions for landscape videos (and videos whose shape is unknown). */
    public static boolean captionsLandscape() {
        return captions();
    }

    /** Captions for vertical videos. */
    public static boolean captionsVertical() {
        return captions();
    }

    /** Captions for videos opened from a youtube.com/shorts link. */
    public static boolean captionsShorts() {
        return captions();
    }

    /** Upstream name of {@link #captionsLandscape()}, kept for ported call sites. */
    public static boolean youtubeCaptionsLandscape() {
        return captionsLandscape();
    }

    /** Upstream name of {@link #captionsVertical()}, kept for ported call sites. */
    public static boolean youtubeCaptionsVertical() {
        return captionsVertical();
    }

    /** Upstream name of {@link #captionsShorts()}, kept for ported call sites. */
    public static boolean youtubeCaptionsShorts() {
        return captionsShorts();
    }
}
