package krio.systemdesign.chatapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import krio.systemdesign.chatapp.core.designsystem.theme.ChatAppTheme
import krio.systemdesign.chatapp.core.designsystem.theme.SystemBarsAppearance
import krio.systemdesign.chatapp.crossfeature.domain.repository.SessionRepository
import krio.systemdesign.chatapp.navigation.AppNavHost
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val sessionRepository: SessionRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Not enableEdgeToEdge() from androidx.activity: on a configuration change it reapplies the styles
        // of its first call and brings back the translucent scrim under the navigation buttons.
        WindowCompat.enableEdgeToEdge(window)
        setContent {
            // Starts in the system theme; the chat list button switches it until the app is closed.
            val isSystemDarkTheme = isSystemInDarkTheme()
            var darkTheme by rememberSaveable { mutableStateOf(isSystemDarkTheme) }
            // Null until the session is read from the database: nothing is shown for that moment.
            val isSignedIn by remember {
                sessionRepository.session.map { it != null }
            }.collectAsStateWithLifecycle(null)
            SystemBarsAppearance(window, darkTheme)
            ChatAppTheme(darkTheme = darkTheme) {
                isSignedIn?.let {
                    AppNavHost(
                        isSignedIn = it,
                        onToggleTheme = { darkTheme = !darkTheme },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
