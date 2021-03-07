package com.khoutz.serializer

import com.khoutz.model.UserDTO
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializes a [UserDTO.id] to a string, rather than the whole class
 */
object UserIdSerializer: KSerializer<UserDTO> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("userId", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): UserDTO {
        throw UnsupportedOperationException()
    }

    override fun serialize(encoder: Encoder, value: UserDTO) {
        encoder.encodeString(value.id.toString())
    }
}