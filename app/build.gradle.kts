import dev.detekt.gradle.extensions.FailOnSeverity
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    id("com.android.application")
    id("dev.detekt")
    id("org.jetbrains.kotlinx.kover")
}

android {
    namespace = "com.github.panlelapin.mementomori"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.github.panlelapin.mementomori"
        minSdk = 34
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"

        ndk {
            abiFilters += setOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            // Bootstrap signing only: installable, reproducible, and not for production distribution.
            signingConfig = signingConfigs.getByName("debug")
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

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        lintConfig = file("lint.xml")
        warningsAsErrors = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
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
