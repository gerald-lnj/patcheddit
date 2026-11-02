/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.redditisfun;

import android.app.Activity;
import android.widget.Toast;

/**
 * The two Redgifs fixes that cannot be done in an OkHttp interceptor.
 * <p>
 * Redgifs disabled rif's OAuth client (401 ClientDisabled) and rif pins api.redgifs.com to
 * certificates Redgifs has since replaced. The token itself is handled by
 * {@link FixRedgifsApiPatch}, which extends the shared interceptor.
 */
@SuppressWarnings("unused")
public final class Redgifs {
    private Redgifs() {
    }

    /**
     * Hooked into OkHttp's {@code CertificatePinner.check(hostname, cleanedPeerCertificatesFn)}:
     * skip rif's stale pins for this host only. Normal certificate checks remain.
     */
    public static boolean skipPin(String host) {
        return "api.redgifs.com".equals(host);
    }

    /**
     * Hooked into the Redgifs video loader's failure path when Redgifs answered 404 (the video was
     * deleted): say so and close the viewer instead of falling back to the browser.
     */
    public static boolean removed(Activity activity) {
        if (activity == null) {
            return false;
        }
        Toast.makeText(activity, "This Redgifs video was removed", Toast.LENGTH_LONG).show();
        activity.onBackPressed();
        return true;
    }
}
