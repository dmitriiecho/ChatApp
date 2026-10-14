package krio.systemdesign.chatapp.crossfeature.data.remote

import krio.systemdesign.chatapp.contract.ConversationDto
import krio.systemdesign.chatapp.contract.MessageDto
import krio.systemdesign.chatapp.crossfeature.data.local.ConversationEntity
import krio.systemdesign.chatapp.crossfeature.data.local.LastMessageEmbedded
import krio.systemdesign.chatapp.crossfeature.data.local.MessageEntity

internal fun MessageDto.toEntity() = MessageEntity(
    id = id,
    conversationId = conversationId,
    seq = seq,
    version = version,
    authorId = authorId,
    authorName = authorName,
    text = text,
    sentAtMillis = sentAtMillis,
    editedAtMillis = editedAtMillis,
    isDeleted = isDeleted,
)

internal fun ConversationDto.toEntity() = ConversationEntity(
    id = id,
    title = title,
    avatarUrl = avatarUrl,
    sortKey = sortKey,
    version = version,
    unreadCount = unreadCount,
    lastSeq = lastSeq,
    lastReadSeq = lastReadSeq,
    lastMessage = lastMessage?.let {
        LastMessageEmbedded(
            id = it.id,
            seq = it.seq,
            authorId = it.authorId,
            authorName = it.authorName,
            text = it.text,
            sentAtMillis = it.sentAtMillis,
            editedAtMillis = it.editedAtMillis,
            isDeleted = it.isDeleted,
        )
    },
)
