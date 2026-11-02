/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.redditisfun;

import static app.morphe.extension.shared.Utils.getContext;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Pattern;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.fixes.slink.BaseFixSLinksPatch;
import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * rif's share-link handler, on top of the shared /s/ link resolution.
 * <p>
 * Ported from the ReVanced "Fix share links" patch: share links
 * ({@code reddit.com/r/{sub}/s/{code}}) are resolved to the post's permalink before rif's link
 * router sees them, so rif opens them like any other permalink instead of loading the share link in
 * its in-app browser. The shared {@link BaseFixSLinksPatch} does the resolution (and needs rif's
 * access token for it, which {@link TokenCapture} hands over when rif makes an authenticated
 * request); this class adds the rif-specific parts:
 *
 * <ul>
 *   <li>{@code reddit.com/media?url=…} image links, which rif otherwise treats as a short link to
 *       the post with the id "media" (a real r/funny post) — plus the Matrix media links that
 *       redirect to them;</li>
 *   <li>falling back to rif's own browser instead of the web-view activity the shared base would
 *       open (rif has no WebViewActivity the extension could name);</li>
 *   <li>resolving without an access token, as a browser would, when rif has not made an
 *       authenticated request yet.</li>
 * </ul>
 */
@SuppressWarnings("unused")
public class FixSLinksPatch extends BaseFixSLinksPatch {
    static {
        INSTANCE = new FixSLinksPatch();
    }

    /** The shared base appends this to a link it could not resolve. */
    private static final String BYPASS_SUFFIX = "#bypass";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0 Mobile Safari/537.36";

    private static final int TIMEOUT_MS = 5000;

    /** A share link can redirect to another share link; give up after a few hops. */
    private static final int MAX_REDIRECTS = 4;

    /** Codes are case-sensitive: never lowercase. */
    private static final Pattern SHARE_PATH = Pattern.compile("^/(r|u|user)/[^/]+/s/[A-Za-z0-9]+/?$");

    /** Reddit's Matrix media links; the id redirects to its i.redd.it image. */
    private static final Pattern MATRIX_MEDIA = Pattern.compile(
            "^https?://matrix\\.redditspace\\.com/_matrix/media/v\\d+/download/reddit\\.com/[A-Za-z0-9]+/?(\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * webViewActivityClass is deliberately left unset: rif's web-view activity is not part of the
     * extension's compile class path, and {@link #resolveSLink} handles the shared base's
     * {@code #bypass} case itself by handing the link back to rif.
     */
    private FixSLinksPatch() {
    }

    // region Hooks called from rif's bytecode.

    /**
     * Called at the top of rif's link router. Returns true if the link is being handled here, in
     * which case the router must return without doing anything.
     */
    public static boolean patchResolveSLink(String link) {
        if (INSTANCE == null || link == null) {
            return false;
        }
        return INSTANCE.resolveSLink(link);
    }

    /**
     * Called with rif's OAuth access token; the shared base needs it to resolve /s/ links
     * anonymously otherwise.
     */
    public static void patchSetAccessToken(String accessToken) {
        if (INSTANCE != null && accessToken != null && !accessToken.isEmpty()) {
            INSTANCE.setAccessToken(accessToken);
        }
    }

    /**
     * Called at the top of MainActivity's VIEW-intent dispatcher, before it reads the intent.
     * <p>
     * A share link from another app would otherwise open rif's in-app browser, which starts a
     * second MainActivity. Instead the intent is pointed at the subreddit (rif still has to set up
     * its state — returning early from the dispatcher broke Back and onSaveInstanceState) and the
     * permalink is opened in a new activity once it is resolved.
     */
    public static void patchHandleViewIntent(Activity activity) {
        if (INSTANCE == null || activity == null) {
            return;
        }
        try {
            final Intent intent = activity.getIntent();
            if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
                return;
            }
            final Uri data = intent.getData();
            if (data == null) {
                return;
            }
            final String url = data.toString();
            if (url.endsWith(BYPASS_SUFFIX)) {
                return; // already given up on: let rif open it as it would have.
            }

            final String media = mediaTarget(url);
            if (media != null) {
                // rif reads /media as a short link to the post with the id "media".
                intent.setData(Uri.parse(media));
                return;
            }

            // /u/ share links go to rif's user activity instead, not here.
            if (!isShareLink(url, false)) {
                return;
            }

            final Uri parsed = Uri.parse(url);
            if (parsed.getPathSegments().size() < 2) {
                return;
            }
            intent.setData(Uri.parse("https://www.reddit.com/r/" + parsed.getPathSegments().get(1)));
            INSTANCE.resolveSLink(url);
        } catch (Throwable t) {
            Logger.printException(() -> "patchHandleViewIntent failed", t);
        }
    }

    /**
     * Captures rif's OAuth access token from an authenticated request, so /s/ links can be resolved
     * with it when rif has one. Registered alongside the other interceptors in
     * {@code http/OkHttpRequestHook}.
     */
    public static Interceptor tokenCapture() {
        return new TokenCapture();
    }

    // endregion

    @Override
    public boolean resolveSLink(String link) {
        if (link == null) {
            return false;
        }
        try {
            if (link.endsWith(BYPASS_SUFFIX)) {
                // The shared base has given up: let rif handle the link as it would without the patch.
                openInRif(link);
                return true;
            }

            final String media = mediaTarget(link);
            if (media != null) {
                if (isMatrixMedia(media)) {
                    resolveMatrixMedia(media);
                } else {
                    openInRif(media);
                }
                return true;
            }

            if (isShareLink(link, true) && accessToken == null) {
                // No token yet: resolve it the way a browser would, which is what the ReVanced patch
                // does for every share link. The token only makes the lookup survive an IP ban.
                resolveInBackground(link);
                return true;
            }

            return super.resolveSLink(link);
        } catch (Throwable t) {
            Logger.printException(() -> "resolveSLink failed", t);
            return false;
        }
    }

    // region Link handling.

    private static void resolveInBackground(@NonNull String link) {
        Utils.runOnBackgroundThread(() -> {
            final String resolved = followRedirects(link);
            openInRif(resolved != null ? resolved : link + BYPASS_SUFFIX);
        });
    }

    /**
     * Opens [url] in rif: an ACTION_VIEW intent to our own package, which is how the shared base
     * hands a resolved link back to the app too.
     */
    private static void openInRif(@NonNull String url) {
        try {
            final Context context = getContext();
            if (context == null) {
                return;
            }
            final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setPackage(context.getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            Logger.printException(() -> "openInRif failed", t);
        }
    }

    /**
     * Follows the share link's redirects without an access token; returns the permalink, or null if
     * anything is off.
     */
    @Nullable
    private static String followRedirects(@NonNull String link) {
        String current = toWww(link);
        for (int hop = 0; hop < MAX_REDIRECTS; hop++) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(current).openConnection();
                connection.setRequestMethod("HEAD");
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setRequestProperty("User-Agent", USER_AGENT);

                final int code = connection.getResponseCode();
                if (code != 301 && code != 302 && code != 303 && code != 307 && code != 308) {
                    return current.equals(link) ? null : clean(current);
                }
                final String location = connection.getHeaderField("Location");
                if (location == null) {
                    return null;
                }
                final URL next = new URL(new URL(current), location);
                if (!isRedditHost(next.getHost())) {
                    return null;
                }
                final String path = next.getPath();
                if (path == null || path.startsWith("/login")) {
                    return null;
                }
                final String nextUrl = next.toString();
                if (isShareLink(nextUrl, true)) {
                    current = toWww(nextUrl);
                    continue;
                }
                return clean(nextUrl);
            } catch (Throwable t) {
                Logger.printException(() -> "Failed to resolve " + link, t);
                return null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        return null;
    }

    /** Follows a Matrix media link to the i.redd.it image it redirects to, and opens that. */
    private static void resolveMatrixMedia(@NonNull String url) {
        Utils.runOnBackgroundThread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestMethod("HEAD");
                // The next hop sends browsers to reddit.com/media.
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setRequestProperty("User-Agent", USER_AGENT);

                final int code = connection.getResponseCode();
                final String location = connection.getHeaderField("Location");
                if (code >= 300 && code < 400 && location != null) {
                    final URL next = new URL(new URL(url), location);
                    if (isRedditMediaHost(next.getHost())) {
                        openInRif(next.toString());
                        return;
                    }
                }
            } catch (Throwable t) {
                Logger.printException(() -> "Failed to resolve " + url, t);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            // Unresolved: let rif open the link as it would have.
            openInRif(url);
        });
    }

    /** The link inside a {@code reddit.com/media?url=…} wrapper, or null if this is not one. */
    @Nullable
    static String mediaTarget(String url) {
        if (url == null) {
            return null;
        }
        final Uri u = Uri.parse(url);
        final String scheme = u.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            return null;
        }
        if (!isRedditHost(u.getHost())) {
            return null;
        }
        final String path = u.getPath();
        if (!"/media".equals(path) && !"/media/".equals(path)) {
            return null;
        }
        final String target = u.getQueryParameter("url");
        if (target == null) {
            return null;
        }
        final Uri t = Uri.parse(target);
        final String targetScheme = t.getScheme();
        if (!"https".equalsIgnoreCase(targetScheme) && !"http".equalsIgnoreCase(targetScheme)) {
            return null;
        }
        // Only Reddit-hosted targets are unwrapped: others keep rif's behaviour.
        return isRedditMediaHost(t.getHost()) || isMatrixMedia(target) ? target : null;
    }

    static boolean isMatrixMedia(String url) {
        return url != null && MATRIX_MEDIA.matcher(url).matches();
    }

    /** [allowUser] false rejects /u/ and /user/ links, which rif routes elsewhere. */
    private static boolean isShareLink(String url, boolean allowUser) {
        if (url == null) {
            return false;
        }
        final Uri u = Uri.parse(url);
        final String scheme = u.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            return false;
        }
        if (!isRedditHost(u.getHost())) {
            return false;
        }
        final String path = u.getPath();
        if (path == null || !SHARE_PATH.matcher(path).matches()) {
            return false;
        }
        return allowUser || path.startsWith("/r/");
    }

    private static boolean isRedditHost(@Nullable String host) {
        if (host == null) {
            return false;
        }
        host = host.toLowerCase();
        return host.equals("reddit.com") || host.endsWith(".reddit.com");
    }

    private static boolean isRedditMediaHost(@Nullable String host) {
        if (host == null) {
            return false;
        }
        host = host.toLowerCase();
        return host.equals("redd.it") || host.endsWith(".redd.it");
    }

    /** old./np./m./... pages differ from www (old. redirects to a login wall). */
    private static String toWww(@NonNull String url) {
        return Uri.parse(url).buildUpon()
                .scheme("https")
                .authority("www.reddit.com")
                .build()
                .toString();
    }

    /** Drops share_id and utm_* tracking parameters, keeps the rest (e.g. context). */
    private static String clean(@NonNull String url) {
        final Uri u = Uri.parse(url);
        final Uri.Builder builder = u.buildUpon().clearQuery().fragment(null);
        for (String name : u.getQueryParameterNames()) {
            if (name.equals("share_id") || name.startsWith("utm_")) {
                continue;
            }
            for (String value : u.getQueryParameters(name)) {
                builder.appendQueryParameter(name, value);
            }
        }
        return builder.build().toString();
    }

    // endregion

    /**
     * Remembers rif's OAuth access token for /s/ link resolution. Reads it off rif's own API
     * requests, which is the only place the token is available to an interceptor without hooking
     * rif's (obfuscated) token store.
     */
    private static final class TokenCapture implements Interceptor {
        private static final String BEARER = "Bearer ";

        @NonNull
        @Override
        public Response intercept(@NonNull Chain chain) throws IOException {
            final String authorization = chain.request().header("Authorization");
            if (authorization != null && authorization.startsWith(BEARER)) {
                patchSetAccessToken(authorization.substring(BEARER.length()).trim());
            }
            return chain.proceed(chain.request());
        }
    }
}
