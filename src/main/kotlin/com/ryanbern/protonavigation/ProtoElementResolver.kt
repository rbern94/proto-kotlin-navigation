package com.ryanbern.protonavigation

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

object ProtoElementResolver {

    fun resolve(protoFile: PsiFile, target: ProtoTarget): PsiElement? {
        val text = protoFile.text

        return when (target.kind) {
            ProtoElementKind.MESSAGE -> resolveTopLevelOrNested(protoFile, text, "message", target.elementName, target.parentName)
            ProtoElementKind.ENUM -> resolveTopLevelOrNested(protoFile, text, "enum", target.elementName, target.parentName)
            ProtoElementKind.SERVICE -> resolveDeclaration(protoFile, text, "service", target.elementName)
            ProtoElementKind.RPC_METHOD -> resolveRpcMethod(protoFile, text, target.elementName, target.parentName)
            ProtoElementKind.FIELD -> resolveField(protoFile, text, target.elementName, target.parentName)
        }
    }

    private fun resolveTopLevelOrNested(
        file: PsiFile,
        text: String,
        keyword: String,
        name: String,
        parentName: String?,
    ): PsiElement? {
        if (parentName != null) {
            // Find the parent scope first, then search within it
            val parentOffset = findDeclarationOffset(text, "message", parentName)
                ?: findDeclarationOffset(text, "enum", parentName)
                ?: return resolveDeclaration(file, text, keyword, name)

            val braceStart = text.indexOf('{', parentOffset)
            if (braceStart < 0) return null
            val braceEnd = findMatchingBrace(text, braceStart) ?: return null

            val innerText = text.substring(braceStart, braceEnd + 1)
            val innerOffset = findDeclarationOffset(innerText, keyword, name)
            if (innerOffset != null) {
                return file.findElementAt(braceStart + innerOffset)
            }
        }

        return resolveDeclaration(file, text, keyword, name)
    }

    private fun resolveDeclaration(file: PsiFile, text: String, keyword: String, name: String): PsiElement? {
        val offset = findDeclarationOffset(text, keyword, name) ?: return null
        return file.findElementAt(offset)
    }

    private fun resolveRpcMethod(file: PsiFile, text: String, rpcName: String, serviceName: String?): PsiElement? {
        val searchText = if (serviceName != null) {
            // Find the service scope first
            val serviceOffset = findDeclarationOffset(text, "service", serviceName) ?: return null
            val braceStart = text.indexOf('{', serviceOffset)
            if (braceStart < 0) return null
            val braceEnd = findMatchingBrace(text, braceStart) ?: return null

            val innerText = text.substring(braceStart, braceEnd + 1)
            val rpcPattern = Regex("""rpc\s+$rpcName\s*\(""")
            val match = rpcPattern.find(innerText)
            if (match != null) {
                return file.findElementAt(braceStart + match.range.first)
            }
            return null
        } else {
            val rpcPattern = Regex("""rpc\s+$rpcName\s*\(""")
            val match = rpcPattern.find(text)
            if (match != null) {
                return file.findElementAt(match.range.first)
            }
            return null
        }
    }

    private fun resolveField(file: PsiFile, text: String, fieldName: String, parentName: String?): PsiElement? {
        val searchScope: String
        val baseOffset: Int

        if (parentName != null) {
            val parentOffset = findDeclarationOffset(text, "message", parentName) ?: return null
            val braceStart = text.indexOf('{', parentOffset)
            if (braceStart < 0) return null
            val braceEnd = findMatchingBrace(text, braceStart) ?: return null
            searchScope = text.substring(braceStart, braceEnd + 1)
            baseOffset = braceStart
        } else {
            searchScope = text
            baseOffset = 0
        }

        // Match field declaration: <type> field_name = N;
        // Also handle: repeated <type> field_name = N; / optional <type> field_name = N; / map<K,V> field_name = N;
        val fieldPattern = Regex("""(?:repeated\s+|optional\s+|map<[^>]+>\s+)?[\w.]+\s+($fieldName)\s*=""")
        val match = fieldPattern.find(searchScope)
        if (match != null) {
            val group = match.groups[1]!!
            return file.findElementAt(baseOffset + group.range.first)
        }

        return null
    }

    private fun findDeclarationOffset(text: String, keyword: String, name: String): Int? {
        val pattern = Regex("""$keyword\s+$name\s*\{""")
        return pattern.find(text)?.range?.first
    }

    private fun findMatchingBrace(text: String, openIndex: Int): Int? {
        var depth = 0
        for (i in openIndex until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return null
    }
}
