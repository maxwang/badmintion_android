package com.badmintonledger.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.abs
import kotlin.math.round

/** Money as integer cents. On the JSON wire (backup contract v1) it is a dollar number. */
@JvmInline
@Serializable(with = CentsAsDollarsSerializer::class)
value class Cents(val value: Long)

fun dollarsToCents(dollars: Double): Long = round(dollars * 100).toLong()

fun centsToDollars(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val a = abs(cents)
    return "$sign${a / 100}.${(a % 100).toString().padStart(2, '0')}"
}

object CentsAsDollarsSerializer : KSerializer<Cents> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Cents", PrimitiveKind.DOUBLE)

    override fun serialize(
        encoder: Encoder,
        value: Cents,
    ) {
        encoder.encodeDouble(value.value / 100.0)
    }

    override fun deserialize(decoder: Decoder): Cents = Cents(dollarsToCents(decoder.decodeDouble()))
}
