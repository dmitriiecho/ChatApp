import com.jraska.module.graph.assertion.GraphRulesExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

// The module rules from CLAUDE.md ("Modules"), checked by ./gradlew assertModuleGraph (CI runs it too).
// Only the dependencies listed here are allowed: any other one fails the check, so a new kind of dependency
// between modules is a decision made here. Each rule is one regex over the whole ":from -> :to" text.
// Applied to the app: the check walks its graph.
internal fun Project.configureModuleGraphRules() {
    pluginManager.apply(libs.plugins.module.graph.assertion.get().pluginId)

    extensions.configure<GraphRulesExtension> {
        allowed = arrayOf(
            // core knows nothing about chats.
            """:core:.* -> :core:.*""",
            // crossfeature builds on core; within crossfeature only data uses the domain (ui takes plain values).
            """:crossfeature:(domain|ui) -> :core:.*""",
            """:crossfeature:data -> :(core:.*|crossfeature:domain)""",
            // A feature's ui uses only the design system and the shared components.
            """:feature:\w+:ui -> :(core:designsystem|crossfeature:ui)""",
            // A feature's impl: core, crossfeature without data, and its own ui, never another feature.
            """:feature:\w+:impl -> :(core:.*|crossfeature:(domain|ui))""",
            """:feature:(\w+):impl -> :feature:\1:ui""",
            """:app -> :(core|crossfeature|feature):.*""",
        )
    }
}
