/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.redditisfun;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import okhttp3.HttpUrl;

/**
 * Helpers for the imgur patches.
 *
 * <ul>
 *   <li>{@link #proxyUrl(HttpUrl)}: rif fetched imgur data through its own proxy
 *       (api.redditisfun.com/api/imgur/…), which no longer exists. Rewrites those URLs to
 *       api.imgur.com, which serves the same v3 paths.</li>
 *   <li>{@link #fixAlbumCover(String)}: imgur's album JSON often has an empty {@code cover}, so the
 *       album's thumbnail cannot be built; fall back to the first image's id.</li>
 *   <li>{@link #albumId(String)}: newer album links put the title in the path
 *       (/a/some-album-title-AbC123); the API only takes the id after the last dash.</li>
 *   <li>{@link #clientId()}: rif's own imgur client id, which its other imgur requests already send.</li>
 * </ul>
 */
public final class Imgur {
    /**
     * rif's own imgur API client ID, already shipped in the app and sent by its other imgur
     * requests (k3.a / k3.b in the free build).
     */
    private static final String CLIENT_ID = "Client-ID 4d7e2f74f1a519c";

    /** Host of rif's dead imgur proxy. */
    private static final String PROXY_HOST = "api.redditisfun.com";

    /** Host that serves imgur's public v3 API. */
    private static final String API_HOST = "api.imgur.com";

    private Imgur() {
    }

    /** rif's imgur client id, sent as {@code Authorization: Client-ID …}. */
    public static String clientId() {
        return CLIENT_ID;
    }

    /**
     * Trims a titled album id to the id itself: {@code some-album-title-AbC123} → {@code AbC123}.
     * Plain imgur ids never contain a dash, so an id without one is returned unchanged.
     */
    public static String albumId(String id) {
        if (id == null) {
            return null;
        }
        int dash = id.lastIndexOf('-');
        return dash < 0 ? id : id.substring(dash + 1);
    }

    /**
     * Rewrites a request URL that went through rif's imgur proxy to imgur's public API, preserving
     * the rest of the path and the query. Returns {@code url} itself (same instance) when the URL
     * is not an imgur proxy URL, so callers can compare by identity.
     *
     * <p>{@code /3/album/<id>/cover} only ever existed on the proxy; {@code /3/album/<id>} has the
     * same fields, so that trailing segment is dropped.
     */
    public static HttpUrl proxyUrl(HttpUrl url) {
        if (url == null || !PROXY_HOST.equals(url.host())) {
            return url;
        }

        List<String> segments = url.pathSegments();
        if (segments.size() < 2
                || !"api".equals(segments.get(0))
                || !"imgur".equals(segments.get(1))) {
            return url;
        }

        // /api/imgur/<rest…> → /<rest…>
        HttpUrl.Builder builder = url.newBuilder()
                .scheme("https")
                .host(API_HOST);
        builder.removePathSegment(0);
        builder.removePathSegment(0);
        if (segments.size() == 6
                && "3".equals(segments.get(2))
                && "album".equals(segments.get(3))
                && "cover".equals(segments.get(5))) {
            builder.removePathSegment(3);
        }
        return builder.build();
    }

    /**
     * Returns [json] with an empty album {@code cover} filled from the first image's id, or
     * {@code null} when nothing changed (not JSON, no album, or a cover is already set).
     */
    public static String fixAlbumCover(String json) {
        if (json == null) {
            return null;
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONObject album = root.optJSONObject("data");
            if (album != null) {
                return fillCover(album) ? root.toString() : null;
            }
            // Gallery listings put the albums in an array.
            JSONArray albums = root.optJSONArray("data");
            if (albums == null) {
                return null;
            }
            boolean changed = false;
            for (int i = 0; i < albums.length(); i++) {
                JSONObject item = albums.optJSONObject(i);
                if (item != null && fillCover(item)) {
                    changed = true;
                }
            }
            return changed ? root.toString() : null;
        } catch (JSONException e) {
            return null; // Not an imgur JSON response; leave it alone.
        }
    }

    /** Sets {@code cover} from {@code images[0].id} when it is missing or empty. */
    private static boolean fillCover(JSONObject album) {
        if (!album.optString("cover", "").isEmpty()) {
            return false;
        }
        JSONArray images = album.optJSONArray("images");
        if (images == null || images.length() == 0) {
            return false;
        }
        JSONObject first = images.optJSONObject(0);
        if (first == null) {
            return false;
        }
        String id = first.optString("id", "");
        if (id.isEmpty()) {
            return false;
        }
        try {
            album.put("cover", id);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }
}
