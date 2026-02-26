package com.ryanbern.protonavigation

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope

enum class ProtoElementKind {
    MESSAGE,
    ENUM,
    SERVICE,
    FIELD,
    RPC_METHOD,
}

data class ProtoTarget(
    val sourceProtoPath: String?,
    val javaPackage: String,
    val elementName: String,
    val kind: ProtoElementKind,
    val parentName: String? = null,
)

object GeneratedCodeMapper {

    fun map(element: PsiElement): ProtoTarget? {
        val containingFile = element.containingFile ?: return null
        val fileText = containingFile.text
        val sourceProtoPath = extractSourceProtoPath(fileText)
        val javaPackage = extractPackage(element, fileText) ?: return null

        return when (element) {
            is PsiClass -> mapClass(element, sourceProtoPath, javaPackage)
            is PsiMethod -> mapMethod(element, sourceProtoPath, javaPackage)
            is PsiField -> mapField(element, sourceProtoPath, javaPackage)
            else -> null
        }
    }

    private fun mapClass(
        psiClass: PsiClass,
        sourceProtoPath: String?,
        javaPackage: String,
    ): ProtoTarget? {
        val className = psiClass.name ?: return null
        val strippedName = ProtoNameConverter.stripGeneratedSuffix(className)

        val kind = when {
            className.endsWith("GrpcKt") || className.endsWith("Grpc") -> ProtoElementKind.SERVICE
            psiClass.isEnum -> ProtoElementKind.ENUM
            className.endsWith("OrBuilder") -> ProtoElementKind.MESSAGE
            className.endsWith("Kt") -> ProtoElementKind.MESSAGE
            else -> ProtoElementKind.MESSAGE
        }

        // Handle nested classes: OuterMessage.InnerMessage
        val parentClass = psiClass.containingClass
        val parentName = if (parentClass != null) {
            val parentClassName = parentClass.name ?: return null
            ProtoNameConverter.stripGeneratedSuffix(parentClassName)
        } else {
            null
        }

        val effectiveSourcePath = sourceProtoPath ?: extractSourceFromGrpcKtStub(psiClass)

        return ProtoTarget(
            sourceProtoPath = effectiveSourcePath,
            javaPackage = javaPackage,
            elementName = strippedName,
            kind = kind,
            parentName = parentName,
        )
    }

    private fun mapMethod(
        method: PsiMethod,
        sourceProtoPath: String?,
        javaPackage: String,
    ): ProtoTarget? {
        val methodName = method.name
        val containingClass = method.containingClass ?: return null
        val containingClassName = containingClass.name ?: return null

        if (isGrpcStubClass(containingClass)) {
            val rpcName = methodName.replaceFirstChar { it.uppercaseChar() }
            val serviceName = ProtoNameConverter.stripGeneratedSuffix(
                findGrpcServiceClassName(containingClass) ?: containingClassName,
            )

            val effectiveSourcePath = sourceProtoPath ?: extractSourceFromGrpcKtStub(containingClass)

            return ProtoTarget(
                sourceProtoPath = effectiveSourcePath,
                javaPackage = javaPackage,
                elementName = rpcName,
                kind = ProtoElementKind.RPC_METHOD,
                parentName = serviceName,
            )
        }

        val fieldName = ProtoNameConverter.methodToProtoFieldName(methodName) ?: return null
        val messageName = ProtoNameConverter.stripGeneratedSuffix(containingClassName)

        val outerClass = containingClass.containingClass
        val parentName = if (outerClass != null) {
            ProtoNameConverter.stripGeneratedSuffix(outerClass.name ?: return null)
        } else {
            null
        }

        return ProtoTarget(
            sourceProtoPath = sourceProtoPath,
            javaPackage = javaPackage,
            elementName = fieldName,
            kind = ProtoElementKind.FIELD,
            parentName = parentName ?: messageName,
        )
    }

    private fun mapField(
        field: PsiField,
        sourceProtoPath: String?,
        javaPackage: String,
    ): ProtoTarget? {
        val containingClass = field.containingClass ?: return null
        val containingClassName = containingClass.name ?: return null

        val fieldName = field.name
        if (fieldName.endsWith("_FIELD_NUMBER")) {
            val protoFieldName = fieldName.removeSuffix("_FIELD_NUMBER").lowercase()
            val messageName = ProtoNameConverter.stripGeneratedSuffix(containingClassName)
            return ProtoTarget(
                sourceProtoPath = sourceProtoPath,
                javaPackage = javaPackage,
                elementName = protoFieldName,
                kind = ProtoElementKind.FIELD,
                parentName = messageName,
            )
        }

        return null
    }

    private fun findGrpcServiceClassName(psiClass: PsiClass): String? {
        // For inner stub classes, get the outer GrpcKt/Grpc class name
        val outerClass = psiClass.containingClass
        if (outerClass != null) {
            val outerName = outerClass.name ?: return null
            if (outerName.endsWith("GrpcKt") || outerName.endsWith("Grpc")) return outerName
        }
        return null
    }

    private fun isGrpcStubClass(psiClass: PsiClass): Boolean {
        val className = psiClass.name ?: return false
        if (className.endsWith("Grpc") || className.endsWith("GrpcKt")) return true

        val outerClass = psiClass.containingClass
        if (outerClass != null) {
            val outerName = outerClass.name ?: return false
            if (outerName.endsWith("GrpcKt") || outerName.endsWith("Grpc")) return true
        }

        if (psiClass.annotations.any { it.qualifiedName == "io.grpc.kotlin.StubFor" }) return true

        return false
    }

    private fun extractSourceProtoPath(fileText: String): String? {
        val regex = Regex("""^// source: (.+\.proto)""", RegexOption.MULTILINE)
        return regex.find(fileText)?.groupValues?.get(1)
    }

    private fun extractPackage(element: PsiElement, fileText: String): String? {
        // For PsiClass, try qualified name first (works for decompiled classes too)
        if (element is PsiClass) {
            val qualifiedName = element.qualifiedName
            val simpleName = element.name
            if (qualifiedName != null && simpleName != null && qualifiedName.endsWith(".$simpleName")) {
                return qualifiedName.removeSuffix(".$simpleName")
            }
        }

        // For methods/fields, try the containing class
        if (element is PsiMethod) {
            val cls = element.containingClass
            if (cls != null) return extractPackage(cls, fileText)
        }
        if (element is PsiField) {
            val cls = element.containingClass
            if (cls != null) return extractPackage(cls, fileText)
        }

        // Fall back to parsing file text
        val regex = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE)
        return regex.find(fileText)?.groupValues?.get(1)
    }

    private fun extractSourceFromGrpcKtStub(psiClass: PsiClass): String? {
        val fileText = psiClass.containingFile?.text ?: return null

        val grpcImportRegex = Regex("""import\s+([\w.]+Grpc)\.""")
        val match = grpcImportRegex.find(fileText)
        if (match != null) {
            val javaGrpcFqn = match.groupValues[1]
            val javaGrpcClass = findClassByFqn(psiClass.project, javaGrpcFqn)
            if (javaGrpcClass != null) {
                val javaFileText = javaGrpcClass.containingFile?.text ?: return null
                return extractSourceProtoPath(javaFileText)
            }
        }
        return null
    }

    private fun findClassByFqn(project: Project, fqn: String): PsiClass? {
        return JavaPsiFacade.getInstance(project)
            .findClass(fqn, GlobalSearchScope.allScope(project))
    }
}
