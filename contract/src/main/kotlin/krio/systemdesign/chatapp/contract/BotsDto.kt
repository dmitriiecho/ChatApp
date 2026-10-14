package krio.systemdesign.chatapp.contract

import kotlinx.serialization.Serializable

// Whether the server's bots act: GET /bots answers with it, PUT /bots takes and answers with it.
@Serializable
data class BotsDto(val enabled: Boolean)
