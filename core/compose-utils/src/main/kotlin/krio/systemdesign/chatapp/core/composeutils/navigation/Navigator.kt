package krio.systemdesign.chatapp.core.composeutils.navigation

import androidx.navigation3.runtime.NavKey

// How a feature moves between its own screens. The app implements it over its back stack.
interface Navigator {
    fun navigate(key: NavKey)

    fun goBack()
}
