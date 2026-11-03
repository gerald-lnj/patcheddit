# rif is fun patches — ReVanced → Morphe port status

This fork adds the `rif is fun` 5.6.22 patches that exist in the ReVanced patch repos
[MojiRS/revanced-rif-patches](https://github.com/MojiRS/revanced-rif-patches) and
[notBrian2/revanced-rif-fixes](https://github.com/notBrian2/revanced-rif-fixes) but had no Morphe
equivalent in [wchill/patcheddit](https://github.com/wchill/patcheddit).

Target: `com.andrewshu.android.reddit` (free) and `com.andrewshu.android.redditdonation`
(golden platinum), both RIF 5.6.22. Obfuscation differs per build; the merged symbol table lives in
`patches/src/main/kotlin/app/morphe/patches/reddit/customclients/redditisfun/shared/RifSymbols.kt`.

## Ported in this fork

| Patch | Source | Notes |
|---|---|---|
| Disable ads | MojiRS | Free build only; 7 fingerprints gated on `Settings.blockAds()` |
| Fix imgur album thumbnails | notBrian2 | Proxy rewrite re-expressed as `ImgurProxyInterceptor`; album-cover fallback at the JSON layer |
| Fix imgur title links | notBrian2 | Trims `/a/some-title-ID` at the album URI builder |
| Use public imgur API | already here | Now also sends the `Authorization: Client-ID …` header the ReVanced patch adds |
| Fix /s/ links | MojiRS | Shared `BaseFixSLinksPatch` + RIF wiring, incl. the `reddit.com/media` unwrap |
| Fix Redgifs API | notBrian2 | Shared token interceptor + cert-pin skip + removed-toast hook |
| Fix comment video links | MojiRS | `VideoLinks.orStreamFallback` via reflection, no rif compile deps |
| Fix YouTube videos | MojiRS | Rewrites the iframe origin to the app package |
| YouTube player controls | MojiRS | Custom player UI, playerVars, state/error bridges |

## Covered by existing Morphe patches (no port needed)

- **Fix Reddit galleries** — `FixRedditApiLookupsPatch` (`/api/info`, `/by_id` 403 handling).
- **Fix load more comments** — `FixMoreCommentsPatch` shims `/api/morechildren` as an OkHttp
  interceptor instead of rewriting the request task.

## Not ported

- **Inline comment images** (MojiRS): 240 LOC patch + 4,156 LOC of extension Java, of which 3,396 LOC
  are two mutually referential classes (`InlineImages`, `InlineVideos`). It also needs the ReVanced
  settings screen (preferences fragment + `StepSeekBarPreference` + a `RifBaseSettingsFragment`
  compile stub). Deferred to its own change: it cannot be verified without a 5.6.22 APK, and the
  partial-move failure mode is a silent compile break.

## Settings

Upstream ships a ReVanced preferences screen. This port has no settings UI: features are
default-on through `extensions/redditisfun/.../Settings.java` (a static shim), except YouTube
captions, which default off to match the upstream checkbox defaults. A future preferences screen
would back those getters with `SharedPreferences`.

## Verification status

Verified here:

- `./gradlew :patches:buildAndroid :patches:generatePatchesList` succeeds; the bundle embeds the new
  extension classes in `extensions/redditisfun.mpe`.
- `patches-list.json` reports 14 RIF patches (6 before this work).
- Extension Java compiles against the Android SDK and OkHttp 5.0.0-alpha.11; the imgur helpers were
  exercised on the JVM (16 cases) by the porting harness.

Not verified (needs a real APK):

- Fingerprint resolution on the actual 5.6.22 free/platinum dex. The fingerprints are copied
  condition-for-condition from the upstream patches, but nothing here has run the patcher against a
  real APK, so a shape mismatch would surface as a `PatchException` at patch time.
- The Redgifs cert-pin hook. `mapping.txt` only proves the class (`okhttp3.CertificatePinner ->
  okhttp3.g`); the member name comes from the notBrian2 patch and javap of the shipped OkHttp.
  Because patcheddit redirects RIF's okhttp references onto its own bundled copy, the hook is
  applied to the bundled class by shape and, when present, to RIF's native one.
- The imgur Client-ID header hook shape (structural scan of the album loader inner classes).
