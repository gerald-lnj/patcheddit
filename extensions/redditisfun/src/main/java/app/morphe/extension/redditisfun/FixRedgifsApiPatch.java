/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.redditisfun;

import app.morphe.extension.shared.fixes.redgifs.BaseFixRedgifsApiPatch;

/**
 * rif's Redgifs API client.
 * <p>
 * The shared interceptor fetches an anonymous temporary token for api.redgifs.com and answers
 * rif's dead {@code /v2/oauth/client} login with an emulated OAuth reply, so rif's Redgifs videos
 * load again. Registered on rif's OkHttp client by
 * {@code app.morphe.extension.redditisfun.http.OkHttpRequestHook}.
 */
@SuppressWarnings("unused")
public class FixRedgifsApiPatch extends BaseFixRedgifsApiPatch {
    static {
        INSTANCE = new FixRedgifsApiPatch();
    }

    /**
     * Redgifs issues the temporary token to the user agent it was asked with and rejects later
     * requests that carry another one, so the interceptor pins this value on every Redgifs request.
     * A browser user agent is used because api.redgifs.com sits behind a CDN that refuses app and
     * Dalvik agent strings.
     */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0 Mobile Safari/537.36";

    @Override
    public String getDefaultUserAgent() {
        return USER_AGENT;
    }
}
