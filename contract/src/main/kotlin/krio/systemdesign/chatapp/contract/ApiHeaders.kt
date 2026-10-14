package krio.systemdesign.chatapp.contract

// Headers every request and the events socket carry. Without a valid pair the server answers 401.
object ApiHeaders {
    // The key the app is built with (local.properties); the server reads its copy from API_KEY.
    const val API_KEY = "X-Api-Key"

    // Who is asking: one of GET /users. There are no passwords, the user is picked on the first screen.
    const val USER_ID = "X-User-Id"
}
