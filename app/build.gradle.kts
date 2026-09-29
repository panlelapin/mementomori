import dev.detekt.gradle.extensions.FailOnSeverity
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.gradle.api.artifacts.dsl.LockMode

plugins {
    id("com.android.application")
    id("dev.detekt")
    id("org.jetbrains.kotlinx.kover")
}

val robolectricSdk by configurations.creating

android {
    namespace = "com.github.panlelapin.mementomori"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.github.panlelapin.mementomori"
        minSdk = 34
        targetSdk = 36
        versionCode = 2
        versionName = "1.0"

        ndk {
            abiFilters += setOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig =
                signingConfigs.create("distribution") {
                    storeFile =
                        providers.environmentVariable("RELEASE_STORE_FILE").orNull?.let(::file)
                    storePassword =
                        providers.environmentVariable("RELEASE_STORE_PASSWORD").orNull
                    keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
                    keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
                }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        lintConfig = file("lint.xml")
        warningsAsErrors = true
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.fragment:fragment:1.9.1")
    implementation("com.google.android.material:material:1.14.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    robolectricSdk("org.robolectric:android-all-instrumented:14-robolectric-10818077-i7")
}

val prepareRobolectricSdk by tasks.registering(Sync::class) {
    from(robolectricSdk)
    into(layout.buildDirectory.dir("robolectric-sdk"))
}

dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
}

// Release packaging cannot bypass the same tests used locally; this does not package an APK locally.
tasks.matching { it.name == "assembleRelease" }.configureEach {
    dependsOn(rootProject.tasks.named("qualityCheck"))
}

tasks.withType<Test>().configureEach {
    dependsOn(prepareRobolectricSdk)
    systemProperty("robolectric.offline", "true")
    systemProperty(
        "robolectric.dependency.dir",
        layout.buildDirectory
            .dir("robolectric-sdk")
            .get()
            .asFile.absolutePath,
    )
    maxHeapSize = "2g"
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
    )
}

detekt {
    toolVersion = "2.0.0-alpha.5"
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = true
    parallel = true
    ignoreFailures = false
    failOnSeverity = FailOnSeverity.Warning
    basePath.set(rootProject.projectDir)
}

kover {
    reports {
        filters {
            includes {
                classes(
                    "com.github.panlelapin.mementomori.AlarmTimeCalculator",
                    "com.github.panlelapin.mementomori.CountdownCalculator",
                    "com.github.panlelapin.mementomori.TargetDatePolicy",
                    "com.github.panlelapin.mementomori.WidgetColorSelector",
                    "com.github.panlelapin.mementomori.WidgetFontSizeCalculator",
                )
            }
        }
        verify {
            rule("Pure calculation coverage") {
                minBound(90)
                minBound(80, CoverageUnit.BRANCH)
            }
        }
    }
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    jvmTarget.set("17")
    exclude("**/build/**", "**/generated/**")
    reports {
        checkstyle.required.set(true)
        html.required.set(true)
        markdown.required.set(true)
        sarif.required.set(true)
    }
}
