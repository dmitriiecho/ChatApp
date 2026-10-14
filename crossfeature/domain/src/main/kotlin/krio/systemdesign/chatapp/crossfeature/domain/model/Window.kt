package krio.systemdesign.chatapp.crossfeature.domain.model

// The part of a chat a screen shows: messages fromSeq to toSeq. toSeq == null: up to the newest message cached
// without a gap, so new messages join the window by themselves.
data class Window(
    val fromSeq: Long,
    val toSeq: Long?,
)
