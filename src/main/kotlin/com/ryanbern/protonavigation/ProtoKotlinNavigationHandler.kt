package com.ryanbern.protonavigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtFile

class ProtoKotlinNavigationHandler : GotoDeclarationHandler {

    private val log = Logger.getInstance(ProtoKotlinNavigationHandler::class.java)

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        if (sourceElement == null) return null
        if (sourceElement.containingFile !is KtFile) return null

        try {
            return doResolve(sourceElement)
        } catch (e: Exception) {
            log.warn("Proto navigation failed", e)
            return null
        }
    }

    private fun doResolve(sourceElement: PsiElement): Array<PsiElement>? {
        // Try resolving references from the element and its parent
        val resolvedTarget = resolveReference(sourceElement)
        if (resolvedTarget == null) {
            log.debug("Could not resolve reference for: ${sourceElement.text}")
            return null
        }
        log.debug("Resolved to: ${resolvedTarget.javaClass.simpleName} in ${resolvedTarget.containingFile?.virtualFile?.path}")

        if (!ProtoGeneratedDetector.isProtoGenerated(resolvedTarget)) {
            log.debug("Target is not proto-generated")
            return null
        }
        log.debug("Target is proto-generated")

        val protoTarget = GeneratedCodeMapper.map(resolvedTarget)
        if (protoTarget == null) {
            log.debug("Could not map to proto target")
            return null
        }
        log.debug("Mapped to proto: $protoTarget")

        val project = sourceElement.project
        val protoFile = ProtoFileFinder.findProtoFile(project, protoTarget)
        if (protoFile == null) {
            log.debug("Could not find .proto file for: $protoTarget")
            return null
        }
        log.debug("Found proto file: ${protoFile.virtualFile?.path}")

        // Try to resolve exact element, fall back to the file itself
        val protoElement = ProtoElementResolver.resolve(protoFile, protoTarget)
        if (protoElement == null) {
            log.debug("Could not resolve exact element, navigating to file")
            return arrayOf(protoFile)
        }

        log.debug("Resolved proto element at offset ${protoElement.textOffset}")
        return arrayOf(protoElement)
    }

    private fun resolveReference(sourceElement: PsiElement): PsiElement? {
        // Try the parent's reference first (leaf element → name reference expression)
        val parent = sourceElement.parent
        if (parent != null) {
            parent.reference?.resolve()?.let { return it }
            // Try all references on the parent
            for (ref in parent.references) {
                ref.resolve()?.let { return it }
            }
        }

        // Try the element itself
        sourceElement.reference?.resolve()?.let { return it }
        for (ref in sourceElement.references) {
            ref.resolve()?.let { return it }
        }

        return null
    }

    override fun getActionText(context: DataContext): String = "Go to Proto Definition"
}
