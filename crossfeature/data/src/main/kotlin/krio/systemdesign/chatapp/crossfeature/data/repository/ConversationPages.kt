package krio.systemdesign.chatapp.crossfeature.data.repository

import krio.systemdesign.chatapp.contract.ConversationsPageDto

// Rules for the chat list's pages, shared by the repository and the sync.

internal const val CONVERSATIONS_PAGE_SIZE = 50

// An empty page counts as the end of the list too: otherwise loading more would go round forever.
internal val ConversationsPageDto.hasMoreBelow: Boolean
    get() = hasMore && items.isNotEmpty()

// The boundary after this page: the sortKey of its lowest chat. With nothing below, everything is loaded and the
// boundary goes to the very bottom, so a new chat with a sortKey below the old boundary shows in a short list.
internal fun ConversationsPageDto.boundaryBelow(): Long = if (hasMoreBelow) items.last().sortKey else Long.MIN_VALUE
