package util

import org.gradle.kotlin.dsl.DependencyHandlerScope

internal fun DependencyHandlerScope.commonMainImplementation(dependencyNotation: Any) {
    "commonMainImplementation"(dependencyNotation)
}

internal fun DependencyHandlerScope.implementation(dependencyNotation: Any) {
    "implementation"(dependencyNotation)
}
