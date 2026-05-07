package digital.heirlooms.ui.share

private val TAG_PATTERN = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
private const val MAX_LENGTH = 50

fun isValidTag(tag: String): Boolean =
    tag.isNotEmpty() && tag.length <= MAX_LENGTH && TAG_PATTERN.matches(tag)
