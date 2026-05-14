package digital.heirlooms.server.routes.upload

val ALLOWED_MIME_TYPE_PATTERNS: Set<Regex> = setOf(
    Regex("^image/.+"),
    Regex("^video/.+"),
)

fun isAllowedMimeType(mimeType: String): Boolean {
    val normalised = mimeType.substringBefore(";").trim().lowercase()
    return ALLOWED_MIME_TYPE_PATTERNS.any { it.matches(normalised) }
}
