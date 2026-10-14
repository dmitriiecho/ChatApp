package krio.systemdesign.chatapp.core.composeutils

import com.github.takahirom.roborazzi.AndroidComposePreviewTester
import com.github.takahirom.roborazzi.ComposePreviewTester
import com.github.takahirom.roborazzi.ComposePreviewTester.TestParameter.JUnit4TestParameter.AndroidPreviewJUnit4TestParameter
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi

// The screenshot tests' tester (chatapp.android.screenshots): Roborazzi's own, with Compose's clock stopped.
// The default one waits until Compose is idle, which never comes with an endless animation; with
// the clock stopped every preview is drawn at its first frame, the same on every run.
@OptIn(ExperimentalRoborazziApi::class)
class PausedClockPreviewTester(private val tester: AndroidComposePreviewTester = AndroidComposePreviewTester()) :
    ComposePreviewTester<AndroidPreviewJUnit4TestParameter> by tester {

    override fun test(testParameter: AndroidPreviewJUnit4TestParameter) {
        testParameter.composeTestRule.mainClock.autoAdvance = false
        tester.test(testParameter)
    }
}
