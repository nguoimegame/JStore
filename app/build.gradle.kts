/*
 * SPDX-FileCopyrightText: 2021-2025 Rahul Kumar Patel <whyorean@gmail.com>
 * SPDX-FileCopyrightText: 2022-2025 The Calyx Institute
 * SPDX-FileCopyrightText: 2023 grrfe <grrfe@420blaze.it>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.parcelize)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.hilt.android.plugin)
}

val lastCommitHash = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
}.standardOutput.asText.map { it.trim() }

val lastCommitTimestamp = providers.exec {
    commandLine("git", "log", "-1", "--format=%ct")
}.standardOutput.asText.map { it.trim() }

// jstore: the release workflow passes the version from the git tag (vX.Y.Z); local builds
// are 0.0.0-dev. The signing key comes from the environment (CI) or signing.properties and
// must be JaecooApp's key, or JaecooApp will not install for jstore.
val jstoreVersionName = providers.gradleProperty("jstoreVersionName").getOrElse("0.0.0-dev")
val jstoreVersionCode = providers.gradleProperty("jstoreVersionCode").getOrElse("1").toInt()
val jstoreKeystore: String? = System.getenv("JSTORE_KEYSTORE")?.takeIf { it.isNotBlank() }

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xannotation-default-target=param-property"
        )
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi",
            "coil3.annotation.ExperimentalCoilApi",
            "kotlin.uuid.ExperimentalUuidApi"
        )
    }
}

configure<ApplicationExtension> {
    namespace = "com.aurora.store"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "com.example.jstore"
        minSdk {
            // jstore: runs next to JaecooApp, which needs Android 10
            version = release(29)
        }
        targetSdk {
            version = release(37)
        }

        versionCode = jstoreVersionCode
        versionName = jstoreVersionName

        testInstrumentationRunner = "com.aurora.store.HiltInstrumentationTestRunner"
        testInstrumentationRunnerArguments["disableAnalytics"] = "true"

        // jstore: Exodus is not offered, so no key is shipped
        buildConfigField("String", "EXODUS_API_KEY", "\"\"")
        buildConfigField("long", "BUILD_TIMESTAMP", "${lastCommitTimestamp.get()}L")

        missingDimensionStrategy("device", "vanilla")
    }

    signingConfigs {
        if (jstoreKeystore != null) {
            create("release") {
                storeFile = file(jstoreKeystore)
                storePassword = System.getenv("JSTORE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("JSTORE_KEY_ALIAS")
                keyPassword = System.getenv("JSTORE_KEY_PASSWORD")
            }
        } else if (rootProject.file("signing.properties").exists()) {
            create("release") {
                val properties = Properties().apply {
                    rootProject.file("signing.properties").inputStream().use { load(it) }
                }

                keyAlias = properties["KEY_ALIAS"] as String
                keyPassword = properties["KEY_PASSWORD"] as String
                storeFile = file(properties["STORE_FILE"] as String)
                storePassword = properties["KEY_PASSWORD"] as String
            }
        }
        create("aosp") {
            // Generated from the AOSP test key:
            // https://android.googlesource.com/platform/build/+/refs/tags/android-11.0.0_r29/target/product/security/testkey.pk8
            keyAlias = "testkey"
            keyPassword = "testkey"
            storeFile = file("testkey.jks")
            storePassword = "testkey"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }

        register("nightly") {
            initWith(getByName("release"))
            applicationIdSuffix = ".nightly"
            versionNameSuffix = "-${lastCommitHash.get()}"
        }

        debug {
            // jstore: same package and key as a release, so JaecooApp grants the contract
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("aosp")
        }
    }

    flavorDimensions += "device"

    productFlavors {
        create("vanilla") {
            isDefault = true
            dimension = "device"
            // jstore: Aurora's dispenser only serves Aurora itself; sign in through microG
            buildConfigField("Boolean", "SHOW_ANONYMOUS_LOGIN", "false")
        }

        // jstore: the huawei and preload flavors are not built
    }

    buildFeatures {
        buildConfig = true
        aidl = true
        compose = true
    }

    lint {
        lintConfig = file("lint.xml")
    }

    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf(
            "en", "ar", "es", "fr", "in", "id", "kk", "pt", "ru", "th", "vi", "zh"
        )
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        dex {
            useLegacyPackaging = true
        }
    }
}

androidComponents {
    beforeVariants(selector().all()) { variant ->
        val flavour = variant.flavorName
        if ((flavour == "huawei" || flavour == "preload") && variant.buildType == "nightly") {
            variant.enable = false
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

ktlint {
    android = true
    verbose = true
}

dependencies {

    // Google's Goodies
    implementation(libs.google.android.material)
    implementation(libs.google.protobuf.javalite)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.navigation3)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.paging.runtime)

    implementation(libs.androidx.adaptive.core)
    implementation(libs.androidx.adaptive.navigation)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Coil
    implementation(libs.coil.kt)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)

    // HTTP Clients
    implementation(libs.squareup.okhttp)

    // Lib-SU
    implementation(libs.github.topjohnwu.libsu)

    // GPlayApi
    implementation(libs.auroraoss.gplayapi)

    // Shizuku
    implementation(libs.rikka.shizuku.api)
    implementation(libs.rikka.shizuku.provider)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.google.truth)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.google.truth)
    androidTestImplementation(libs.androidx.espresso.core)

    // Hilt
    ksp(libs.hilt.android.compiler)
    ksp(libs.hilt.androidx.compiler)
    implementation(libs.androidx.hilt.viewmodel)
    implementation(libs.hilt.android.core)
    implementation(libs.hilt.androidx.work)

    kspAndroidTest(libs.hilt.android.compiler)
    androidTestImplementation(libs.hilt.android.testing)

    // Room
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.paging)

    implementation(libs.process.phoenix)

    // LeakCanary
    debugImplementation(libs.squareup.leakcanary.android)
}

// ---- jstore additions, kept together at the end so merging upstream stays small ----
//
// Branding: upstream's strings (and their Weblate translations, which Weblate rewrites
// wholesale) say "Aurora Store" in many places. Rather than editing them, each build
// generates overrides with the product name replaced, so the app reads "jstore" while the
// source files stay identical to upstream. "Aurora OSS" (the authors) and "Aurora Services"
// (another product) keep their names.

abstract class JstoreBrandTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceRes: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        val entry =
            Regex("""<string\s+name="([^"]+)"([^>]*)>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        val bareName = Regex("""Aurora(?! OSS| Services| Support|OSS)""")

        sourceRes.get().asFile.listFiles { dir -> dir.isDirectory && dir.name.startsWith("values") }
            ?.forEach { dir ->
                val strings = File(dir, "strings.xml").takeIf { it.exists() } ?: return@forEach
                val overrides = entry.findAll(strings.readText())
                    .filter { "Aurora" in it.groupValues[3] }
                    .map { match ->
                        val (name, attrs, text) = match.destructured
                        val branded = bareName.replace(
                            text.replace("Aurora Store", "jstore"),
                            "jstore"
                        )
                        """    <string name="$name"$attrs>$branded</string>"""
                    }
                    .toList()
                if (overrides.isEmpty()) return@forEach
                File(out, dir.name).apply {
                    mkdirs()
                }.resolve("strings_jstore_brand.xml").writeText(
                    (
                        listOf("<?xml version=\"1.0\" encoding=\"utf-8\"?>", "<resources>") +
                            overrides + "</resources>"
                        ).joinToString("\n", postfix = "\n")
                )
            }
    }
}

val jstoreBrand = tasks.register<JstoreBrandTask>("jstoreBrandStrings") {
    sourceRes.set(layout.projectDirectory.dir("src/main/res"))
    outputDir.set(layout.buildDirectory.dir("generated/jstore/brandRes"))
}

androidComponents.onVariants { variant ->
    variant.sources.res?.addGeneratedSourceDirectory(jstoreBrand, JstoreBrandTask::outputDir)
}
