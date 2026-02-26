package com.ryanbern.protonavigation

object ProtoNameConverter {

    fun camelToSnake(name: String): String {
        if (name.isEmpty()) return name
        val result = StringBuilder()
        for ((i, ch) in name.withIndex()) {
            if (ch.isUpperCase()) {
                if (i > 0) {
                    val prev = name[i - 1]
                    val next = name.getOrNull(i + 1)
                    // Insert underscore before uppercase if preceded by lowercase,
                    // or if preceded by uppercase and followed by lowercase (acronym boundary)
                    if (prev.isLowerCase() || (prev.isUpperCase() && next != null && next.isLowerCase())) {
                        result.append('_')
                    }
                }
                result.append(ch.lowercaseChar())
            } else {
                result.append(ch)
            }
        }
        return result.toString()
    }

    fun methodToProtoFieldName(methodName: String): String? {
        val stripped = when {
            methodName.startsWith("get") && methodName.length > 3 -> methodName.removePrefix("get")
            methodName.startsWith("has") && methodName.length > 3 -> methodName.removePrefix("has")
            methodName.startsWith("set") && methodName.length > 3 -> methodName.removePrefix("set")
            else -> return null
        }

        // Strip trailing "List" for repeated fields (e.g., getBalancesList -> balances)
        val withoutListSuffix = if (stripped.endsWith("List") && stripped.length > 4) {
            stripped.removeSuffix("List")
        } else {
            stripped
        }

        return camelToSnake(withoutListSuffix)
    }

    fun stripGeneratedSuffix(className: String): String = when {
        className.endsWith("OrBuilder") -> className.removeSuffix("OrBuilder")
        className.endsWith("GrpcKt") -> className.removeSuffix("GrpcKt")
        className.endsWith("Grpc") -> className.removeSuffix("Grpc")
        className.endsWith("Kt") -> className.removeSuffix("Kt")
        else -> className
    }
}
