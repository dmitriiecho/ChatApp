package krio.systemdesign.chatapp.crossfeature.domain.model

// A message written but not on the server yet. clientId is its id until the server gives it a seq: a resend
// with the same one doesn't post it twice.
data class OutgoingMessage(
    val clientId: String,
    val conversationId: String,
    val text: String,
    val createdAtMillis: Long,
    // The last attempt failed; it is retried when the network is back or on a tap.
    val isFailed: Boolean,
)
