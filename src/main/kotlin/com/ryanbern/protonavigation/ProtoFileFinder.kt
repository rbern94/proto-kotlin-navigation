package com.ryanbern.protonavigation

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

object ProtoFileFinder {

    private val log = Logger.getInstance(ProtoFileFinder::class.java)

    private val PROTO_ROOT_PATTERNS = listOf(
        "/proto/",
        "/src/main/proto/",
    )

    fun findProtoFile(project: Project, target: ProtoTarget): PsiFile? =
        findBySourcePath(project, target) ?: findByPackageSearch(project, target)

    private fun findBySourcePath(project: Project, target: ProtoTarget): PsiFile? {
        val sourcePath = target.sourceProtoPath ?: return null
        val fileName = sourcePath.substringAfterLast('/')

        // Use allScope to find files even in non-source directories
        val candidates = FilenameIndex.getVirtualFilesByName(
            fileName,
            GlobalSearchScope.allScope(project),
        )
        log.debug("Source path search for '$fileName': found ${candidates.size} candidates")

        val psiManager = PsiManager.getInstance(project)

        // Filter out files in target/build directories
        val sourceOnlyCandidates = candidates.filter { file ->
            !file.path.contains("/target/") && !file.path.contains("/build/")
        }

        // Match by full source path suffix
        for (file in sourceOnlyCandidates) {
            if (file.path.endsWith(sourcePath)) {
                log.debug("Matched by path suffix: ${file.path}")
                return psiManager.findFile(file)
            }
        }

        // Match by checking known proto root patterns
        for (file in sourceOnlyCandidates) {
            for (pattern in PROTO_ROOT_PATTERNS) {
                val idx = file.path.indexOf(pattern)
                if (idx >= 0) {
                    val relativePath = file.path.substring(idx + pattern.length)
                    if (relativePath == sourcePath) {
                        log.debug("Matched by proto root pattern: ${file.path}")
                        return psiManager.findFile(file)
                    }
                }
            }
        }

        // Broadest match: just the filename in a proto directory
        for (file in sourceOnlyCandidates) {
            if (PROTO_ROOT_PATTERNS.any { file.path.contains(it) }) {
                log.debug("Fallback match by proto root: ${file.path}")
                return psiManager.findFile(file)
            }
        }

        return null
    }

    private fun findByPackageSearch(project: Project, target: ProtoTarget): PsiFile? {
        val allProtoFiles = FilenameIndex.getAllFilesByExt(
            project,
            "proto",
            GlobalSearchScope.allScope(project),
        )
        log.debug("Package search: found ${allProtoFiles.size} total .proto files")

        val psiManager = PsiManager.getInstance(project)
        val javaPackagePattern = Regex("""option\s+java_package\s*=\s*"([^"]+)"""")

        val matchingFiles = mutableListOf<VirtualFile>()
        for (file in allProtoFiles) {
            if (file.path.contains("/target/") || file.path.contains("/build/")) continue

            val psiFile = psiManager.findFile(file) ?: continue
            val text = psiFile.text
            val match = javaPackagePattern.find(text) ?: continue
            if (match.groupValues[1] == target.javaPackage) {
                matchingFiles.add(file)
            }
        }

        log.debug("Package search: ${matchingFiles.size} files match java_package=${target.javaPackage}")

        if (matchingFiles.isEmpty()) return null

        if (matchingFiles.size == 1) {
            return psiManager.findFile(matchingFiles[0])
        }

        // Multiple files share the same java_package — find the one containing the target element
        val elementName = target.parentName ?: target.elementName
        for (file in matchingFiles) {
            val psiFile = psiManager.findFile(file) ?: continue
            val text = psiFile.text
            if (containsElement(text, elementName, target.kind)) {
                log.debug("Package search: matched element '$elementName' in ${file.path}")
                return psiFile
            }
        }

        return psiManager.findFile(matchingFiles[0])
    }

    private fun containsElement(text: String, name: String, kind: ProtoElementKind): Boolean {
        val patterns = when (kind) {
            ProtoElementKind.MESSAGE, ProtoElementKind.FIELD -> listOf("message $name", "enum $name")
            ProtoElementKind.ENUM -> listOf("enum $name")
            ProtoElementKind.SERVICE, ProtoElementKind.RPC_METHOD -> listOf("service $name")
        }
        return patterns.any { text.contains(it) }
    }
}
