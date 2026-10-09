package ru.grabovsky.poibot.util

import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.function.Function

class DefaultInstantDeserializer : InstantDeserializer<Instant>(
    Instant::class.java,
    DateTimeFormatter.ISO_INSTANT,
    Function { temporal: TemporalAccessor? -> Instant.from(temporal) },
    Function { a: FromIntegerArguments? -> Instant.ofEpochMilli(a!!.value) },
    Function { a: FromDecimalArguments? -> Instant.ofEpochSecond(a!!.integer, a.fraction.toLong()) },
    null,
    true,
    true,
    true
)