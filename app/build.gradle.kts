import dev.detekt.gradle.extensions.FailOnSeverity

plugins {
    id("com.android.application")
    id("dev.detekt")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.github.panlelapin.mementomori"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.github.panlelapin.mementomori"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += setOf("arm64-v8a")
        }
    }

    buildFeatures {
        compose = true
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
    implementation("androidx.glance:glance-appwidget:1.1.1")
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
