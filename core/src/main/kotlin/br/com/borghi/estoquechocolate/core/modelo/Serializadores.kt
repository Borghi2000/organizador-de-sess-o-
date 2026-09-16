package br.com.borghi.estoquechocolate.core.modelo

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate
import java.time.LocalDateTime

/** Grava datas no backup como texto ISO (2026-09-16), legivel por humanos. */
object SerializadorData : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("DataIso", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

/** Grava data e hora no backup como texto ISO (2026-09-16T14:30:00). */
object SerializadorDataHora : KSerializer<LocalDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("DataHoraIso", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDateTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDateTime = LocalDateTime.parse(decoder.decodeString())
}
