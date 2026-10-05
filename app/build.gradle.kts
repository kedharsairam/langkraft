plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.krafttools.langkraft"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.krafttools.langkraft"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        // The corpus is JSONL in assets, parsed once at startup. No Room, no
        // SQLite, no generated schema — see ContentRepository for why.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Debug-signed, as in every other Kraft app: a published APK should be
            // installable directly, and there is no release keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    androidResources {
        // JSONL must STAY COMPRESSED. The corpus is the whole app and there is no
        // AssetManager.openFd anywhere — ContentRepository streams it — so the
        // usual "add noCompress for .json" advice does not apply and would make
        // the APK several times larger for nothing.
        //
        // EnglishKraft recorded the same lesson in reverse: forcing noCompress on
        // its database took the debug APK from 450 MB to 895 MB. Measure the APK,
        // do not reason about it.
    }
}

// The corpus assets are build outputs and are gitignored, so nothing can compile or
// test without them. Wiring the pipeline into the build means a clean checkout works,
// and it means the assets can never be stale relative to specs/ and content/ — which is
// the failure that would ship a Tier 0 the learner never sees the source of.
//
// Fails the build rather than skipping if npm is unavailable. A silently skipped emit
// would produce an APK with no content, which looks identical to a working one.
// Lint BEFORE emit, as a real dependency rather than a convention anyone has to
// remember. `emit` only JSON.parses each line; every guarantee the Kotlin parser relies
// on (required fields, enums, cross-record links, provenance) is enforced by the
// linters. Building with `assembleDebug` alone previously skipped them entirely, so
// `npm run check` passing said nothing about the APK that shipped.
val lintAssets = tasks.register<Exec>("lintAssets") {
    group = "verification"
    description = "Fails the build on invalid specs or content."
    workingDir = rootProject.file("pipeline")
    commandLine("npm", "run", "--silent", "lint:all")
    // Glyph coverage is verified here too, not only in `npm run check`. A build that does not
    // fail on a missing glyph is a build that ships tofu, and the previous eight "bundled"
    // fonts were all HTML error pages that nothing checked.
    commandLine("python3", "content/font-coverage.py")
    inputs.dir(rootProject.file("specs"))
    inputs.dir(rootProject.file("content"))
    inputs.dir(rootProject.file("app/src/main/res/font"))
}

val emitAssets = tasks.register<Exec>("emitAssets") {
    dependsOn(lintAssets)
    workingDir = rootProject.file("pipeline")
    commandLine("npm", "run", "--silent", "emit")
    inputs.dir(rootProject.file("specs"))
    inputs.dir(rootProject.file("content"))
    outputs.dir(rootProject.file("app/src/main/assets"))
}

tasks.matching { it.name.startsWith("generate") && it.name.contains("Assets", ignoreCase = true) }
    .configureEach { dependsOn(emitAssets) }

tasks.named("preBuild") { dependsOn(lintAssets, emitAssets) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.navigation:navigation-compose:2.10.1")
    implementation("androidx.activity:activity-compose:1.12.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.json:json:20250107")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.7.0")

    // Compose UI testing. ui-test-manifest ships the ComponentActivity that
    // createComposeRule needs; it is debugImplementation because it must never reach
    // a release build.
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}