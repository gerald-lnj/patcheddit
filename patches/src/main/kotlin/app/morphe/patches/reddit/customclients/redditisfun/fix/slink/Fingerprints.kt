/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.redditisfun.fix.slink

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

internal const val MAIN_ACTIVITY = "Lcom/andrewshu/android/reddit/MainActivity;"

private fun ClassDef.constStrings(): Set<String> =
    methods.flatMap { method ->
        method.implementation?.instructions
            ?.filter { it.opcode == Opcode.CONST_STRING }
            ?.map { (it as ReferenceInstruction).reference.toString() }
            .orEmpty()
    }.toSet()

/**
 * rif's link router (free `l3.g.q` / golden platinum `m3.g.q`):
 *
 * ```
 * public static q(String url, String, NoOpenType, String, String, boolean, WhitelistStatus,
 *                 FragmentManager, Context, BackStackSource) -> void
 * ```
 *
 * Every link tap inside rif ends up here. Matched by shape rather than by name, in the class that
 * holds both `ACTION_COPY_URL` and `com.android.browser.application_id` — a pair unique to the
 * router class in both builds. The parameter types are only checked where they are not rif's own
 * obfuscated types, since those differ per build.
 */
internal object LinkRouterFingerprint : Fingerprint(
    custom = { method, classDef ->
        AccessFlags.PUBLIC.isSet(method.accessFlags) &&
            AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.returnType == "V" &&
            method.parameterTypes.size == 10 &&
            method.parameterTypes[0].toString() == "Ljava/lang/String;" &&
            method.parameterTypes[7].toString() == "Landroidx/fragment/app/FragmentManager;" &&
            method.parameterTypes[8].toString() == "Landroid/content/Context;" &&
            classDef.constStrings().let { strings ->
                "com.andrewshu.android.reddit.ACTION_COPY_URL" in strings &&
                    "com.android.browser.application_id" in strings
            }
    },
)

/**
 * `MainActivity.l1()V` (same name in both builds): dispatches a VIEW intent to a fragment. A share
 * link opened from another app lands here, and rif would open its in-app browser on it, which
 * starts a second MainActivity.
 */
internal object MainActivityIntentFingerprint : Fingerprint(
    definingClass = MAIN_ACTIVITY,
    name = "l1",
    returnType = "V",
    parameters = emptyList(),
    custom = { method, _ ->
        method.implementation?.instructions?.any { instruction ->
            instruction.opcode == Opcode.CONST_STRING &&
                (instruction as ReferenceInstruction).reference.toString() == "reddit url type %s"
        } == true
    },
)
