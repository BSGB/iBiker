package com.puntl.ibiker.companions

import com.puntl.ibiker.MILLIS_IN_SECOND
import com.puntl.ibiker.MINUTES_IN_HOUR
import com.puntl.ibiker.SECONDS_IN_MINUTE
import java.text.SimpleDateFormat
import java.util.*

class DateTimeProvider {
    companion object {
        fun getDate(millis: Long, format: String): String {
            val formatter = SimpleDateFormat(format, Locale.getDefault())
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = millis
            return formatter.format(calendar.time)
        }

        fun getDeltaTime(millis: Long): Triple<String, String, String> {
            var seconds = millis / MILLIS_IN_SECOND
            var minutes = seconds / SECONDS_IN_MINUTE
            val hours = minutes / MINUTES_IN_HOUR

            seconds %= SECONDS_IN_MINUTE
            minutes %= MINUTES_IN_HOUR

            val secondsString = if (seconds < 10) "0$seconds" else "$seconds"
            val minutesString = if (minutes < 10) "0$minutes" else "$minutes"
            val hoursString = if (hours < 10) "0$hours" else "$hours"

            return Triple(hoursString, minutesString, secondsString)
        }
    }
}