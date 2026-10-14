package krio.systemdesign.chatapp.crossfeature.domain.model

data class User(
    val id: String,
    val name: String,
)

// Who the app is signed in as.
data class Session(
    val userId: String,
    val userName: String,
)
