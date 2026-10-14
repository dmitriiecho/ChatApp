package krio.systemdesign.chatapp.core.network

// The headers every request carries to say who sends it: the app's key and the signed-in user. Asked again for
// each request, so a sign-in or a sign-out applies to the next one.
fun interface AuthHeaders {
    fun headers(): Map<String, String>
}
