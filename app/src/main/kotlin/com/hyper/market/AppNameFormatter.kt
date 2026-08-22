package com.hyper.market

internal fun optimizedAppName(name: String, enabled: Boolean): String {
    if (!enabled) return name
    val separators = listOf(" - ", "－", "-", " | ", "｜", "丨")
    val cutAt = separators.mapNotNull { separator -> name.indexOf(separator).takeIf { it >= 0 } }
        .minOrNull() ?: return name
    return name.substring(0, cutAt).trim().ifBlank { name }
}
