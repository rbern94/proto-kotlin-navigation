package com.ryanbern.protonavigation

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

object ProtoGeneratedDetector {

    fun isProtoGenerated(element: PsiElement): Boolean {
        val containingFile = element.containingFile ?: return false
        return isPathUnderGeneratedSources(containingFile) ||
            hasSourceProtoComment(containingFile) ||
            isProtoTypeHierarchy(element)
    }

    private fun isPathUnderGeneratedSources(file: PsiFile): Boolean {
        val path = file.virtualFile?.path ?: return false
        return path.contains("/generated-sources/protobuf/") ||
            path.contains("/build/generated/source/proto/")
    }

    private fun hasSourceProtoComment(file: PsiFile): Boolean {
        val text = file.text ?: return false
        val linesToCheck = text.lineSequence().take(5)
        return linesToCheck.any { it.trimStart().startsWith("// source:") && it.contains(".proto") }
    }

    private fun isProtoTypeHierarchy(element: PsiElement): Boolean {
        val psiClass = when (element) {
            is PsiClass -> element
            else -> element.parent as? PsiClass ?: return false
        }

        // Check annotation (works for both source and decompiled classes)
        if (psiClass.annotations.any {
                it.qualifiedName == "com.google.protobuf.Generated" ||
                    it.qualifiedName == "javax.annotation.Generated" ||
                    it.qualifiedName == "io.grpc.stub.annotations.GrpcGenerated"
            }) return true

        // Check superclass chain (handles both source and decompiled classes)
        var superClass = psiClass.superClass
        while (superClass != null) {
            if (superClass.qualifiedName in PROTO_SUPER_CLASSES) return true
            superClass = superClass.superClass
        }

        // Check interfaces (including transitive for decompiled classes)
        if (hasProtoInterface(psiClass)) return true

        // Check for Kotlin gRPC stubs via file text imports
        val fileText = psiClass.containingFile?.text ?: return false
        if (fileText.contains("import io.grpc.kotlin.AbstractCoroutineStub") ||
            fileText.contains("import io.grpc.kotlin.AbstractCoroutineServerImpl")
        ) return true

        // Check for protobuf-generated class naming patterns with protobuf superclass
        // (decompiled classes from JARs)
        val path = psiClass.containingFile?.virtualFile?.path ?: return false
        if (path.contains(".jar!/")) {
            // Check if the package contains proto-like types (heuristic for JAR-based resolution)
            if (psiClass.implementsListTypes.any {
                    it.className == "MessageOrBuilder" ||
                        it.className == "ProtocolMessageEnum" ||
                        it.className?.endsWith("OrBuilder") == true
                }) return true
        }

        return false
    }

    private fun hasProtoInterface(psiClass: PsiClass): Boolean {
        val interfaceNames = psiClass.interfaces.mapNotNull { it.qualifiedName }
        return interfaceNames.any { it in PROTO_INTERFACES }
    }

    private val PROTO_SUPER_CLASSES = setOf(
        "com.google.protobuf.GeneratedMessage",
        "com.google.protobuf.GeneratedMessageV3",
        "com.google.protobuf.GeneratedFile",
        "com.google.protobuf.AbstractMessage",
    )

    private val PROTO_INTERFACES = setOf(
        "com.google.protobuf.ProtocolMessageEnum",
        "com.google.protobuf.MessageOrBuilder",
    )
}
