/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.redditisfun.shared

import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

/*
 * Merged obfuscation symbol table for rif is fun 5.6.22 (free + golden platinum).
 *
 * Two upstream ReVanced tables were merged here; the symbol strings were copied verbatim:
 *
 *  - MojiRS/revanced-rif-patches, `patches/src/main/kotlin/app/revanced/patches/rif/shared/Rif.kt`
 *    (`RIF_BUILDS`, `requireScratchRegister`): comment/selftext render + bind symbols.
 *  - notBrian2/revanced-rif-fixes, `patches/src/main/kotlin/app/revanced/patches/riffixes/shared/Rif.kt`
 *    (`RIF_BUILDS`, `method()`, `indexOfOnly()`, `references()`): request task, load-more task, imgur
 *    album builder / JSON parser, okhttp cert pinner, redgifs video loader, comment ids, image id.
 *
 * Every name below is R8-obfuscated output of the final 5.6.22 builds and is therefore *locked to
 * that version*: the free and platinum builds are separate R8 runs, so a name that is right for one
 * build is wrong for the other. Nothing here may be "tidied", renamed, or reused for another version.
 * The two builds are addressed through [RifBuild]; [rifSymbols] looks up a set by build or package
 * name, and [rifSymbols] on a [BytecodePatchContext] tells the builds apart from the APK itself.
 *
 * Do not add symbols here that upstream did not verify, and do not edit a string without re-verifying
 * it against both 5.6.22 APKs.
 */

/** rif is fun (free, ad-supported). */
const val RIF_PACKAGE = "com.andrewshu.android.reddit"

/** rif is fun golden platinum (paid, ad-free). A separate R8 build of the same 5.6.22 code. */
const val RIF_PLATINUM_PACKAGE = "com.andrewshu.android.redditdonation"

/** The rif version the symbol table is obfuscation-locked to. */
const val RIF_VERSION = "5.6.22"

/** Package of rif's own data classes ([RifSymbols.commentParent]'s owner and friends). */
const val THINGS = "Lcom/andrewshu/android/reddit/things/objects"

/** The two 5.6.22 builds. Ads-only patches target [FREE] alone. */
enum class RifBuild(val packageName: String, val label: String) {
    /** Free, ad-supported build. */
    FREE(RIF_PACKAGE, "free"),

    /** Golden platinum, paid build. */
    PLATINUM(RIF_PLATINUM_PACKAGE, "golden platinum");

    /** The obfuscation symbol set of this build. */
    val symbols: RifSymbols get() = RIF_BUILDS.getValue(this)
}

/**
 * The obfuscated names of every member the patches touch, for one 5.6.22 build.
 *
 * Comments state the exact shape each symbol was verified against, so a fingerprint or smali hook can
 * assert it and fail the patch (instead of the app) if a name ever resolves to the wrong member.
 */
data class RifSymbols(
    /** [RifBuild.label] of the build these names belong to; for error messages only. */
    val build: String,

    // ---- comment rendering / binding (MojiRS table) ----

    /** i0.b render callback on CommentThing/ThreadThing: `(SpannableStringBuilder)V`. */
    val renderCallback: String,
    /** Comment ViewHolder whose `h(m, CommentThing, Fragment)` binds the body TextView. */
    val commentBindClass: String,
    /** Thread-list binder whose selftext-preview method calls [selftextGetter] then setText. */
    val selftextBindClass: String,
    /** ThreadThing getter for the rendered selftext: `()CharSequence`. */
    val selftextGetter: String,
    /**
     * Comments-page binder whose `o(holder, ThreadThing)` sets the post's selftext (the body above the
     * comments) via [selftextGetter].
     */
    val opSelftextBindClass: String,

    // ---- request plumbing (notBrian2 table) ----

    /** Request task that sends every rif request: `G()` stores the status in `n:I` and reads the body. */
    val requestTask: String,
    /** okhttp3.Response method returning its headers, called right before the status is stored. */
    val responseHeaders: String,

    // ---- comment tree (notBrian2 table) ----

    /** "Load more comments" task; its constructor's first instruction loads `B:Uri`. */
    val moreTask: String,
    /** Comment sort enum (4th constructor parameter of [moreTask]) and its `()String` API name getter. */
    val sort: String,
    val sortName: String,
    /** CommentThing getters: parent id `()String`, child ids `()String[]`. */
    val commentParent: String,
    val commentChildren: String,
    /** Fragment.getActivity() (the non-throwing one). */
    val fragmentActivity: String,

    // ---- imgur (notBrian2 table) ----

    /** Class with the imgur album URL builder, static `x(String, boolean) -> Uri`. */
    val albumUriBuilder: String,
    /** LoganSquare JSON parser and its `(String default) -> String` value getter. */
    val jsonParser: String,
    val jsonString: String,
    /** ImgurV3ImageItem id getter. */
    val imageId: String,

    // ---- media / TLS (notBrian2 table) ----

    /** Function type of OkHttp CertificatePinner.check(hostname, cleanedPeerCertificatesFn). */
    val pinFunction: String,
    /** Redgifs video loader result class, its callback interface and the callback's "failed" method. */
    val videoTask: String,
    val videoCallback: String,
    val videoFailed: String,
)

/**
 * The whole table. Golden platinum is listed first, then free, matching the order of the notBrian2
 * table; the MojiRS-only names were folded into the same rows, so one lookup yields a build's full set.
 */
internal val RIF_BUILDS: Map<RifBuild, RifSymbols> = mapOf(
    RifBuild.PLATINUM to RifSymbols(
        build = "golden platinum",
        // MojiRS table, platinum row.
        renderCallback = "d", commentBindClass = "Lo2/o;", selftextBindClass = "Lf5/g;",
        selftextGetter = "F0", opSelftextBindClass = "Lf5/u;",
        // notBrian2 table, golden platinum row.
        requestTask = "Li3/c;", responseHeaders = "C", moreTask = "Lq2/b;",
        sort = "Lo2/q;", sortName = "e", commentParent = "F", commentChildren = "z",
        albumUriBuilder = "Lh2/c;", jsonParser = "Lw6/h;", jsonString = "c0", imageId = "l",
        pinFunction = "Lpe/a;", videoTask = "Lb2/d\$b;", videoCallback = "Lw1/y;", videoFailed = "H",
        fragmentActivity = "o1",
    ),
    RifBuild.FREE to RifSymbols(
        build = "free",
        // MojiRS table, free row.
        renderCallback = "e", commentBindClass = "Ln2/o;", selftextBindClass = "Le5/g;",
        selftextGetter = "C0", opSelftextBindClass = "Le5/u;",
        // notBrian2 table, free row.
        requestTask = "Lh3/c;", responseHeaders = "E", moreTask = "Lp2/b;",
        sort = "Ln2/q;", sortName = "d", commentParent = "K", commentChildren = "w",
        albumUriBuilder = "Lg2/c;", jsonParser = "Lx6/h;", jsonString = "e0", imageId = "n",
        pinFunction = "Lof/a;", videoTask = "La2/d\$b;", videoCallback = "Lv1/y;", videoFailed = "O",
        fragmentActivity = "z1",
    ),
)

// ---- public lookups: sibling patches call these instead of copying the table ----

/** The symbol set of [build]. */
fun rifSymbols(build: RifBuild): RifSymbols = build.symbols

/**
 * The symbol set of the app whose package is [packageName].
 *
 * @throws PatchException if [packageName] is neither [RIF_PACKAGE] nor [RIF_PLATINUM_PACKAGE].
 */
fun rifSymbols(packageName: String): RifSymbols =
    RifBuild.entries.firstOrNull { it.packageName == packageName }?.symbols
        ?: throw PatchException(
            "$packageName is not rif is fun $RIF_VERSION ($RIF_PACKAGE or $RIF_PLATINUM_PACKAGE)",
        )

/**
 * The symbol set of the app being patched, told apart by the "load more comments" task's constructor.
 *
 * @throws PatchException unless exactly one build's [RifSymbols.moreTask] declares
 * `<init>(Activity, CommentThing, ThreadThing, sort)`.
 */
fun BytecodePatchContext.rifSymbols(): RifSymbols {
    val matching = RIF_BUILDS.values.filter { symbols ->
        classDefByOrNull(symbols.moreTask)?.methods?.any { method ->
            method.name == "<init>" && method.parameterTypes.map { it.toString() } == listOf(
                "Landroid/app/Activity;", "$THINGS/CommentThing;", "$THINGS/ThreadThing;", symbols.sort,
            )
        } == true
    }
    return matching.singleOrNull()
        ?: throw PatchException("Not rif is fun $RIF_VERSION (free or golden platinum): matched ${matching.size} builds")
}

// ---- per-feature accessors, so each patch group reads only what it needs ----

/** Comment/selftext rendering + binding symbols (MojiRS table). */
data class RifRenderingSymbols(
    val renderCallback: String,
    val commentBindClass: String,
    val selftextBindClass: String,
    val selftextGetter: String,
    val opSelftextBindClass: String,
)

/** Comment-tree symbols: load-more task, sort enum, parent/child ids, fragment fragment accessor. */
data class RifCommentTreeSymbols(
    val moreTask: String,
    val sort: String,
    val sortName: String,
    val commentParent: String,
    val commentChildren: String,
    val fragmentActivity: String,
)

/** Imgur symbols: album URI builder, JSON parser, album image id getter. */
data class RifImgurSymbols(
    val albumUriBuilder: String,
    val jsonParser: String,
    val jsonString: String,
    val imageId: String,
)

/** Media/TLS symbols: request task, okhttp cert pinner, redgifs video loader result + callback. */
data class RifMediaSymbols(
    val requestTask: String,
    val responseHeaders: String,
    val pinFunction: String,
    val videoTask: String,
    val videoCallback: String,
    val videoFailed: String,
)

/** The symbols used to hook comment/selftext rendering. */
val RifSymbols.rendering: RifRenderingSymbols
    get() = RifRenderingSymbols(
        renderCallback = renderCallback,
        commentBindClass = commentBindClass,
        selftextBindClass = selftextBindClass,
        selftextGetter = selftextGetter,
        opSelftextBindClass = opSelftextBindClass,
    )

/** The symbols used by the load-more-comments / comment-tree code. */
val RifSymbols.commentTree: RifCommentTreeSymbols
    get() = RifCommentTreeSymbols(
        moreTask = moreTask,
        sort = sort,
        sortName = sortName,
        commentParent = commentParent,
        commentChildren = commentChildren,
        fragmentActivity = fragmentActivity,
    )

/** The symbols used by the imgur patches. */
val RifSymbols.imgur: RifImgurSymbols
    get() = RifImgurSymbols(
        albumUriBuilder = albumUriBuilder,
        jsonParser = jsonParser,
        jsonString = jsonString,
        imageId = imageId,
    )

/** The symbols used by the request/redgifs/video patches. */
val RifSymbols.media: RifMediaSymbols
    get() = RifMediaSymbols(
        requestTask = requestTask,
        responseHeaders = responseHeaders,
        pinFunction = pinFunction,
        videoTask = videoTask,
        videoCallback = videoCallback,
        videoFailed = videoFailed,
    )

// ---- helpers, copied from the upstream tables ----

/**
 * Throws unless `v0` can safely be used as scratch at [method]'s entry, which is what our
 * injected guards (`move-result v0`) do. That's the case if `v0` is a local (locals come
 * first in the register frame), or if it's a parameter the original code never touches.
 * Otherwise clobbering it corrupts `this`/an argument — a VerifyError at runtime (this is
 * what broke the 1.1.0-rc3 test build). Must be called BEFORE injecting. This turns that into a loud
 * patch-time error instead.
 *
 * Example of the second case: free rif's BannerAdViewHelper.loadAd(View, boolean) has
 * no locals (v0 = this), but its body only uses v1/v2, so the guard is safe there.
 */
fun requireScratchRegister(method: Method) {
    val implementation = method.implementation
        ?: throw PatchException("${method.definingClass}->${method.name} has no implementation")
    val parameterRegisters = method.parameterTypes.sumOf { type ->
        val t = type.toString()
        if (t == "J" || t == "D") 2 else 1
    } + if (AccessFlags.STATIC.isSet(method.accessFlags)) 0 else 1
    if (implementation.registerCount - parameterRegisters >= 1) return // v0 is a local

    if (implementation.instructions.none { 0 in it.registers() }) return // unused parameter

    throw PatchException(
        "${method.definingClass}->${method.name}: v0 is a parameter the method uses; " +
            "an injected v0 scratch would clobber it",
    )
}

/**
 * A method by its class and exact signature; throws if it isn't there.
 *
 * Upstream looked the class up in `classes` and used `proxy(classDef)`; Morphe 2.0.0 has neither on
 * [BytecodePatchContext], so this uses [BytecodePatchContext.mutableClassDefBy] instead. Same result:
 * a mutable method ready for instruction injection.
 */
fun BytecodePatchContext.method(
    type: String,
    name: String,
    parameters: List<String>,
    returnType: String,
): MutableMethod {
    val classDef = classDefByOrNull(type) ?: throw PatchException("$type not found")
    return mutableClassDefBy(classDef).methods.firstOrNull { method ->
        method.name == name &&
            method.returnType == returnType &&
            method.parameterTypes.map { it.toString() } == parameters
    } ?: throw PatchException("$type->$name(${parameters.joinToString("")})$returnType not found")
}

/** The index of the only instruction in this method matching [predicate]; throws if there isn't exactly one. */
fun MutableMethod.indexOfOnly(what: String, predicate: (Instruction) -> Boolean): Int {
    val matches = instructions.withIndex().filter { predicate(it.value) }.map { it.index }
    if (matches.size != 1) {
        throw PatchException("$definingClass->$name: expected one $what, found ${matches.size}")
    }
    return matches.single()
}

/** True if this instruction references exactly [reference] (a method, field, string or type). */
fun Instruction.references(reference: String) =
    (this as? ReferenceInstruction)?.reference?.toString() == reference

/** All registers an instruction reads or writes (wide values: their low register). */
private fun Instruction.registers(): List<Int> = when (this) {
    is RegisterRangeInstruction -> (startRegister until startRegister + registerCount).toList()
    is FiveRegisterInstruction ->
        listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)
    is ThreeRegisterInstruction -> listOf(registerA, registerB, registerC)
    is TwoRegisterInstruction -> listOf(registerA, registerB)
    is OneRegisterInstruction -> listOf(registerA)
    else -> emptyList()
}
