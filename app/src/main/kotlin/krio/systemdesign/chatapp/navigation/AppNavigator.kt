package krio.systemdesign.chatapp.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import krio.systemdesign.chatapp.core.composeutils.navigation.Navigator

// Every navigation in the app goes through here and changes the back stack NavDisplay shows.
class AppNavigator(private val backStack: NavBackStack<NavKey>) : Navigator {

    override fun navigate(key: NavKey) {
        backStack.add(key)
    }

    // The first screen stays: Back from it leaves the app (NavDisplay doesn't handle Back with one screen left).
    override fun goBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }
}
