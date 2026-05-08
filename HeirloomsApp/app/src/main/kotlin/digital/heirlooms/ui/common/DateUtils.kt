package digital.heirlooms.ui.common

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy")

fun formatInstantDate(isoString: String): String = try {
    val instant = Instant.parse(isoString)
    instant.atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FORMAT)
} catch (_: Exception) {
    isoString
}

fun formatOffsetDate(isoString: String): String = try {
    OffsetDateTime.parse(isoString).toLocalDate().format(DATE_FORMAT)
} catch (_: Exception) {
    try {
        // Some dates arrive as plain Instant strings
        formatInstantDate(isoString)
    } catch (_: Exception) {
        isoString
    }
}

fun daysUntilDeletion(compostedAtIso: String): Int = try {
    val composted = Instant.parse(compostedAtIso)
    val deleteAt = composted.plus(90, ChronoUnit.DAYS)
    val remaining = ChronoUnit.DAYS.between(Instant.now(), deleteAt).toInt()
    maxOf(0, remaining)
} catch (_: Exception) {
    0
}
