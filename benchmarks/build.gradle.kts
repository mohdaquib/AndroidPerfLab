plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.aquib.androidperflab.benchmarks"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        // Required: the benchmark runner replaces the standard instrumentation runner.
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"

        // Pass the package of the app under test so Macrobenchmark can launch it.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] =
            "EMULATOR,DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
    }

    buildTypes {
        // "benchmark" must match the build type created in :app so AGP can find the APK.
        create("benchmark") {
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    // Points to the app module so AGP automatically installs it before running tests.
    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.benchmark.junit4)
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.junit)
}
