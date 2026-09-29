import org.gradle.api.artifacts.dsl.LockMode
import org.gradle.api.attributes.Bundling
import org.gradle.language.base.plugins.LifecycleBasePlugin

plugins {
    id("com.android.application") version "9.2.1" apply false
    id("dev.detekt") version "2.0.0-alpha.5" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.8" apply false
}

val ktlint by configurations.creating

dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
}

dependencies {
    ktlint("com.pinterest.ktlint:ktlint-cli:1.8.0") {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        }
    }
}

val ktlintCheck by tasks.registering(JavaExec::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Check all Kotlin source and Gradle Kotlin scripts with KtLint."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args(
        "--relative",
        "**/src/**/*.kt",
        "**.kts",
        "!**/build/**",
    )
}

tasks.register<JavaExec>("ktlintFormat") {
    group = "formatting"
    description = "Format all Kotlin source and Gradle Kotlin scripts with KtLint."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args(
        "--format",
        "--relative",
        "**/src/**/*.kt",
        "**.kts",
        "!**/build/**",
    )
}

tasks.register("qualityCheck") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Run the strict Kotlin quality gate used locally and in CI."
    dependsOn(
        "scriptTests",
        "scriptContract",
        ktlintCheck,
        ":app:detektRelease",
        ":app:koverVerifyDebug",
        ":app:lintRelease",
        ":app:testDebugUnitTest",
    )
}

tasks.register<Exec>("scriptTests") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Run isolated script failure, logging, state and signature regression tests."
    commandLine("python3", "-B", "-m", "unittest", "discover", "-s", "tests", "-v")
}

tasks.register<Exec>("scriptContract") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Check actual scripts, executable bits and the versioned skill snapshot."
    commandLine(
        "bash",
        "-c",
        "source scripts/check-local; check_shell_syntax && check_shellcheck && check_project_contract",
    )
}
