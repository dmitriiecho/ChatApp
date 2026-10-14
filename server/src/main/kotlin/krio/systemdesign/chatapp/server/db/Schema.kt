package krio.systemdesign.chatapp.server.db

// The tables, created on the first start. Booleans are INTEGER 0/1, times are epoch millis.
internal object Schema {
    val statements = listOf(
        // lastSortKey: the user's counter for sortKey, so every chat of the user gets a unique, growing one.
        """CREATE TABLE IF NOT EXISTS users(
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            isBot INTEGER NOT NULL,
            lastSortKey INTEGER NOT NULL DEFAULT 0)""",
        // title is for groups; a direct chat is titled with the other person's name.
        """CREATE TABLE IF NOT EXISTS conversations(
            id TEXT PRIMARY KEY,
            title TEXT,
            isGroup INTEGER NOT NULL,
            lastSeq INTEGER NOT NULL DEFAULT 0)""",
        // A chat as one member sees it: its place in that member's list, its version and what the member read.
        """CREATE TABLE IF NOT EXISTS participants(
            conversationId TEXT NOT NULL REFERENCES conversations(id),
            userId TEXT NOT NULL REFERENCES users(id),
            sortKey INTEGER NOT NULL,
            version INTEGER NOT NULL,
            lastReadSeq INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY(conversationId, userId))""",
        "CREATE INDEX IF NOT EXISTS participants_by_user ON participants(userId, sortKey)",
        // clientId: the sender's id for the message, so a retried send doesn't post it twice.
        // SQLite treats NULLs as distinct, so messages without one (bots, seed data) don't collide.
        """CREATE TABLE IF NOT EXISTS messages(
            id TEXT PRIMARY KEY,
            conversationId TEXT NOT NULL REFERENCES conversations(id),
            seq INTEGER NOT NULL,
            version INTEGER NOT NULL,
            authorId TEXT NOT NULL REFERENCES users(id),
            clientId TEXT,
            text TEXT NOT NULL,
            sentAtMillis INTEGER NOT NULL,
            editedAtMillis INTEGER,
            isDeleted INTEGER NOT NULL DEFAULT 0,
            UNIQUE(conversationId, seq),
            UNIQUE(authorId, clientId))""",
        // The change logs sync tokens point into. AUTOINCREMENT: a position is never reused, even after compaction.
        """CREATE TABLE IF NOT EXISTS conversation_journal(
            position INTEGER PRIMARY KEY AUTOINCREMENT,
            userId TEXT NOT NULL,
            conversationId TEXT NOT NULL,
            removed INTEGER NOT NULL)""",
        "CREATE INDEX IF NOT EXISTS conversation_journal_by_user ON conversation_journal(userId, position)",
        """CREATE TABLE IF NOT EXISTS message_journal(
            position INTEGER PRIMARY KEY AUTOINCREMENT,
            conversationId TEXT NOT NULL,
            messageId TEXT NOT NULL)""",
        "CREATE INDEX IF NOT EXISTS message_journal_by_conversation ON message_journal(conversationId, position)",
        // The oldest position a token may still point to, per log (a user's chat list or one chat).
        """CREATE TABLE IF NOT EXISTS journal_floors(
            scope TEXT PRIMARY KEY,
            floor INTEGER NOT NULL)""",
        // The server's switches (BotsSwitch); a missing key is its default.
        """CREATE TABLE IF NOT EXISTS settings(
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL)""",
    )
}
