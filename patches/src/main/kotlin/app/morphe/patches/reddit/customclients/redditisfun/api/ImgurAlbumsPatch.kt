/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.redditisfun.api

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.redditisfun.shared.rifSymbols
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/redditisfun/ImgurAlbumsPatch;"

/** The imgur client id lives in the extension so both header paths send the same value. */
private const val IMGUR_CLIENT_ID_CALL =
    "Lapp/morphe/extension/redditisfun/Imgur;->clientId()Ljava/lang/String;"

private const val AUTHORIZATION = "Authorization"

val imgurAlbumsPatch = bytecodePatch(
    name = "Use public imgur API",
    description = "Fix imgur albums not loading.",
    default = true
) {
    dependsOn(ExtensionPatches.RIF)
    compatibleWith(*AppCompatibility.RedditIsFun)

    execute {
        val m = ImgurApiFingerprint.method
        m.addInstructions(0, """
            invoke-static       { p0, p1 }, $EXTENSION_CLASS_DESCRIPTOR->buildImgurUri(Ljava/lang/String;Z)Landroid/net/Uri;
            move-result-object p0
            return-object p0
        """)
    }

    // Port of MojiRS FixImgurAlbumsPatch.addClientIdHeader. rif built album requests for its own
    // proxy (api.redditisfun.com), which added imgur's client ID server-side; rif itself only sends
    // an `x-redditisfun-key` header. Rerouted to api.imgur.com the request is anonymous, which
    // imgur's legacy API rejects unpredictably (e.g. 429), so add `Authorization: Client-ID ***`
    // next to rif's existing header.
    //
    // finalize, not execute: the album loader is an inner class of the URL builder's class, which
    // execute above already rewrote, and this hook must be added after every patch has run.
    finalize {
        addClientIdHeader(rifSymbols().albumUriBuilder)
    }
}

/**
 * The album loader's request-header hook (free `g2.c$a.B` / Platinum `h2.c$a.B`):
 * `(settings, okhttp3.Request.Builder, String, Uri) -> void`.
 *
 * The okhttp parameter is accepted both as rif's own (obfuscated) copy and as the complete library
 * the HTTP patch redirects callers to, so this works whichever finalize ran first.
 */
private fun Method.isAlbumRequestHeaderHook(): Boolean =
    returnType == "V" &&
        parameterTypes.size == 4 &&
        isOkHttpRequestBuilder(parameterTypes[1].toString()) &&
        parameterTypes[2].toString() == "Ljava/lang/String;" &&
        parameterTypes[3].toString() == "Landroid/net/Uri;"

private fun isOkHttpRequestBuilder(type: String) =
    type.startsWith("Lokhttp3/") || type.contains("okhttp3/Request\$Builder;")

/** rif's own `Request.Builder.header(name, value)` call. */
private fun Instruction.isBuilderHeaderCall(builderType: String): Boolean {
    val reference = (this as? ReferenceInstruction)?.reference as? MethodReference ?: return false
    return reference.definingClass == builderType &&
        reference.parameterTypes.map { it.toString() } ==
        listOf("Ljava/lang/String;", "Ljava/lang/String;")
}

/**
 * Adds an `Authorization: Client-ID ***` header to every album-loader request-header hook found in
 * an inner class of [builderClass].
 *
 * The name/value registers of rif's own header call are dead after it (the hook returns right
 * after), so they are reused for the second header: no register is clobbered and no frame grows.
 */
private fun BytecodePatchContext.addClientIdHeader(builderClass: String) {
    val innerPrefix = builderClass.removeSuffix(";") + "$"
    val loaders = buildList<String> {
        classDefForEach { classDef ->
            if (classDef.type.startsWith(innerPrefix) && classDef.methods.any { it.isAlbumRequestHeaderHook() }) {
                add(classDef.type)
            }
        }
    }

    for (loader in loaders) {
        for (method in mutableClassDefBy(loader).methods) {
            if (!method.isAlbumRequestHeaderHook()) continue
            val builderType = method.parameterTypes[1].toString()
            val (index, call) = method.instructions.withIndex()
                .lastOrNull { (_, instruction) -> instruction.isBuilderHeaderCall(builderType) }
                ?: continue
            if (method.instructions.getOrNull(index + 1)?.opcode != Opcode.RETURN_VOID) {
                throw PatchException(
                    "$loader->${method.name}: expected return-void after the imgur album header call",
                )
            }
            val registers = call as? FiveRegisterInstruction
                ?: throw PatchException(
                    "$loader->${method.name}: imgur album header call is not a 35c invoke (${call.opcode})",
                )
            if (registers.registerD == registers.registerE) {
                // The name and value would share a register, so reusing them for a second header
                // would clobber one with the other.
                throw PatchException(
                    "$loader->${method.name}: imgur album header call reuses one register for both arguments",
                )
            }
            val reference = (call as ReferenceInstruction).reference
            method.addInstructions(
                index + 1,
                """
                    const-string v${registers.registerD}, "$AUTHORIZATION"
                    invoke-static {}, $IMGUR_CLIENT_ID_CALL
                    move-result-object v${registers.registerE}
                    invoke-virtual { v${registers.registerC}, v${registers.registerD}, v${registers.registerE} }, $reference
                """,
            )
        }
    }
}
