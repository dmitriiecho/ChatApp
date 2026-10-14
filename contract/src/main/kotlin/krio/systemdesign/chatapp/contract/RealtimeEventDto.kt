package krio.systemdesign.chatapp.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// What the server tells a client: one event per frame on the events socket, and in the "events" field of a push.
// Every event carries the whole object as it is now, so applying one twice changes nothing, and version tells a
// late one from a fresh one. In JSON the type is the "type" field.
@Serializable
sealed interface RealtimeEventDto {

    // Socket only: pushes carry no messages.
    @Serializable
    @SerialName("message.new")
    data class MessageNew(val message: MessageDto) : RealtimeEventDto

    // An edit or a deletion (tombstone). Socket only.
    @Serializable
    @SerialName("message.changed")
    data class MessageChanged(val message: MessageDto) : RealtimeEventDto

    // A new message, a read, a rename.
    @Serializable
    @SerialName("conversation.updated")
    data class ConversationUpdated(val conversation: ConversationDto) : RealtimeEventDto

    // The user left the chat or was removed from it.
    @Serializable
    @SerialName("conversation.removed")
    data class ConversationRemoved(val conversationId: String) : RealtimeEventDto

    // Someone turned the server's bots on or off; goes to every open socket. Socket only.
    @Serializable
    @SerialName("bots.changed")
    data class BotsChanged(val enabled: Boolean) : RealtimeEventDto
}

// The data of a push: the events as a JSON array in one string field, because FCM data is Map<String, String>.
object PushData {
    const val EVENTS = "events"
}
