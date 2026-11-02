/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.redditisfun;

import java.io.IOException;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Rewrites rif's imgur proxy requests (api.redditisfun.com/api/imgur/…) to imgur's public API and
 * repairs album responses that carry no cover.
 *
 * <p>This is the Morphe equivalent of the ReVanced patches that hooked {@code
 * okhttp3.Request.Builder.url(String)}: every rif request is built by one OkHttp client, and
 * {@code http/OkHttpRequestHook} installs interceptors on it, so rewriting here needs no
 * obfuscated app symbols (no {@code Request$Builder} fingerprint) and no scratch registers.
 *
 * <p>{@link #isPatchIncluded()} is flipped to {@code true} by the "Fix imgur album thumbnails"
 * patch. While it is {@code false} (patch not applied) the interceptor is inert, so it can be
 * registered unconditionally.
 */
public final class ImgurProxyInterceptor implements Interceptor {
    /** Album JSON is a few KB; anything bigger is not something we rewrite. */
    private static final long MAX_ALBUM_BYTES = 1024L * 1024L;

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /** Overridden by the patch. */
    public boolean isPatchIncluded() {
        return false;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        if (!isPatchIncluded()) {
            return chain.proceed(request);
        }

        HttpUrl url = request.url();
        HttpUrl rewritten = Imgur.proxyUrl(url);
        if (rewritten == url) {
            return chain.proceed(request); // Not an imgur proxy request.
        }

        Request.Builder builder = request.newBuilder().url(rewritten);
        // rif's proxy added the imgur client id server-side; api.imgur.com needs it from us.
        if (request.header("Authorization") == null) {
            builder.header("Authorization", Imgur.clientId());
        }

        Response response = chain.proceed(builder.build());
        return fixAlbumCover(response);
    }

    /**
     * imgur's /3/album/<id> reply often has an empty {@code cover}, which leaves the album without
     * a thumbnail. Falls back to the first image's id, the same value rif's album mapper would have
     * read had imgur set it.
     */
    private static Response fixAlbumCover(Response response) {
        try {
            ResponseBody body = response.body();
            if (body == null || body.contentLength() > MAX_ALBUM_BYTES) {
                return response;
            }

            String json;
            try (ResponseBody peek = response.peekBody(MAX_ALBUM_BYTES)) {
                json = peek.string();
            }

            String fixed = Imgur.fixAlbumCover(json);
            if (fixed == null || fixed.equals(json)) {
                return response;
            }

            MediaType contentType = body.contentType();
            return response.newBuilder()
                    .body(ResponseBody.create(fixed, contentType == null ? JSON : contentType))
                    .build();
        } catch (Exception e) {
            // Repairing the cover is best effort: never fail the request over it.
            return response;
        }
    }
}
