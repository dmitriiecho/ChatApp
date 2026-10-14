package krio.systemdesign.chatapp.core.network

// Where the server is: deployed on the project's machine (server/deploy.sh). Plain HTTP, because there is no
// domain for a certificate; the app allows it for this address only (network_security_config.xml in :app).
object ServerConfig {
    const val BASE_URL = "http://2.56.204.151:8081/"
    const val EVENTS_URL = "ws://2.56.204.151:8081/events"
}
