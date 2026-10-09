package com.saporini.mobile_desktop.pos.orders.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/** Exact decimal text: no binary floating-point rounding or client-side total calculation. */
@Serializable(with = OrderDecimalSerializer::class)
data class OrderDecimal(val value: String) {
    init { require(NUMBER.matches(value)) { "Invalid decimal amount" } }
    val isNegative: Boolean
        get() = value.startsWith("-") && value.substringBefore('e').substringBefore('E').any { it in '1'..'9' }
    override fun toString(): String = value

    companion object {
        private val NUMBER = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")
        val ZERO = OrderDecimal("0.00")
    }
}

internal object OrderDecimalSerializer : KSerializer<OrderDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("OrderDecimal", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): OrderDecimal {
        val text = if (decoder is JsonDecoder) {
            val primitive = decoder.decodeJsonElement() as? JsonPrimitive
                ?: throw SerializationException("Expected a decimal number")
            primitive.content
        } else decoder.decodeString()
        return try { OrderDecimal(text) } catch (error: IllegalArgumentException) {
            throw SerializationException("Invalid decimal amount", error)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: OrderDecimal) {
        if (encoder is JsonEncoder) encoder.encodeJsonElement(JsonUnquotedLiteral(value.value))
        else encoder.encodeString(value.value)
    }
}
