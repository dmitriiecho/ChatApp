package krio.systemdesign.chatapp.contract

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: String,
    val name: String,
)
