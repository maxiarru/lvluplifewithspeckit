package lvluplife.data

import lvluplife.application.OperationBlocked

/** Empty duration fields represent zero; malformed or out-of-range values remain invalid. */
internal fun durationInput(hoursText: String?, minutesText: String?): Pair<Long, Long> {
    fun part(text: String?): Long? = if (text != null && text.isBlank()) 0L else text?.toLongOrNull()
    val hours = part(hoursText)
    val minutes = part(minutesText)
    if (hours == null || minutes == null || hours !in 0..24 || minutes !in 0..59 ||
        (hours == 0L && minutes == 0L) || (hours == 24L && minutes != 0L)) {
        throw OperationBlocked(ApiFailureCode.INVALID_INPUT)
    }
    return hours to minutes
}
