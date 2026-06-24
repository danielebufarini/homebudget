plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room3) apply false
    alias(libs.plugins.skie) apply false
}

extra["spesifyVersionCode"] = 1
extra["spesifyVersionName"] = "1.0"

val verifyUnitTests by tasks.registering {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the shared and Android unit test suite."
    dependsOn(
        ":androidApp:test",
        ":composeApp:testAndroidHostTest"
    )
}

val verifyAndroidDebug by tasks.registering {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Builds the Android debug application."
    dependsOn(":androidApp:assembleDebug")
}

val verifyIosSimulatorArm64Framework by tasks.registering {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Compiles the iOS simulator ARM64 Kotlin framework."
    dependsOn(":composeApp:compileKotlinIosSimulatorArm64")
}

tasks.register("verifyAll") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the project verification suite used locally and in CI."
    dependsOn(
        verifyUnitTests,
        verifyAndroidDebug,
        verifyIosSimulatorArm64Framework
    )
}
