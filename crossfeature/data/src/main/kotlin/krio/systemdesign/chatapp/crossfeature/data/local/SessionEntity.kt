package krio.systemdesign.chatapp.crossfeature.data.local

import androidx.room3.Entity
import androidx.room3.PrimaryKey

// Who the app is signed in as: one row (id = 0) or none.
@Entity(tableName = "session")
internal data class SessionEntity(
    @PrimaryKey val id: Int = 0,
    val userId: String,
    val userName: String,
)
