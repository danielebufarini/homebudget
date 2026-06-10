import co.touchlab.skie.configuration.EnumInterop
import co.touchlab.skie.configuration.FlowInterop
import co.touchlab.skie.configuration.FunctionInterop
import co.touchlab.skie.configuration.SealedInterop
import co.touchlab.skie.configuration.SuspendInterop
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.skie)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    android {
        namespace = "it.danielebufarini.spesify.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources {
            enable = true
        }
        withHostTest {}
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOption("bundleId", "it.danielebufarini.spesify.composeapp")
            binaryOption("smallBinary", "true")
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services.auth)
            implementation(libs.androidx.recyclerview)
            implementation(libs.play.services.auth)
            implementation(libs.google.api.client.android)
            implementation(libs.google.api.client.gson)
            implementation(libs.google.api.services.drive)
            implementation(libs.googleid)
            implementation(libs.mlkit.genai.prompt)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.android)
            implementation(libs.androidx.datastore.preferences)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.voyager.core)
            implementation(libs.voyager.navigator)
            implementation(libs.voyager.transitions)
            implementation(libs.voyager.koin)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        named("androidHostTest").dependencies {
            implementation(libs.androidx.test.core)
            implementation(libs.robolectric)
        }
    }
}

compose {
    resources {
        packageOfResClass = "spesify.composeapp.generated.resources"
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}

skie {
    analytics {
        disableUpload.set(true)
    }
    features {
        coroutinesInterop.set(true)
        defaultArgumentsInExternalLibraries.set(false)
        group {
            EnumInterop.Enabled(true)
            EnumInterop.LegacyCaseName(false)

            SealedInterop.Enabled(true)
            SealedInterop.ExportEntireHierarchy(true)

            FunctionInterop.FileScopeConversion.Enabled(true)
            FunctionInterop.LegacyName(false)

            SuspendInterop.Enabled(true)
            FlowInterop.Enabled(true)
        }
    }
}
