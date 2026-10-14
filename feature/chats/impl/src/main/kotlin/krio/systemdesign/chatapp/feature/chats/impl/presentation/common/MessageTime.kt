package krio.systemdesign.chatapp.feature.chats.impl.presentation.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// A message's time in the device's zone and language: "14:05", and in the list the date when it isn't today.
internal fun messageTime(sentAtMillis: Long): String =
    Instant.ofEpochMilli(sentAtMillis).atZone(ZoneId.systemDefault()).format(TIME)

internal fun listTime(sentAtMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val sent = Instant.ofEpochMilli(sentAtMillis).atZone(zone)
    return if (sent.toLocalDate() == LocalDate.now(zone)) sent.format(TIME) else sent.format(DATE)
}

private val TIME = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val DATE = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
