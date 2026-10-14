import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestStackTraceFilter
import org.gradle.kotlin.dsl.withType

// A failed test prints its message, e.g. AssertK's "expected:<3> but was:<2>", in the build log and in CI's,
// and of the stack trace only the line of the test where it failed: the rest is the test runner's.
internal fun Project.configureTestLogging() {
    tasks.withType<Test>().configureEach {
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
            stackTraceFilters(TestStackTraceFilter.ENTRY_POINT)
        }
    }
}
