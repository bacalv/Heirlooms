package digital.heirlooms.server.domain.upload

private val TAG_REGEX = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
private const val MAX_TAG_LENGTH = 50

sealed class TagValidationResult {
    object Valid : TagValidationResult()
    data class Invalid(val tag: String, val reason: String) : TagValidationResult()
}

fun validateTags(tags: List<String>): TagValidationResult {
    for (tag in tags) {
        val reason = when {
            tag.isEmpty() -> "empty"
            tag.length > MAX_TAG_LENGTH -> "too long (max $MAX_TAG_LENGTH characters)"
            !TAG_REGEX.matches(tag) -> "must be kebab-case: lowercase letters, numbers, and hyphens"
            else -> null
        }
        if (reason != null) return TagValidationResult.Invalid(tag, reason)
    }
    return TagValidationResult.Valid
}
