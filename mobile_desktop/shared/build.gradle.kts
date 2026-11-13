import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()

    androidLibrary {
        namespace = "com.saporini.mobile_desktop.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.ktor.clientOkhttp)
            // Scanning barcodes with the camera: CameraX for the picture, ZXing to read the code.
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            implementation(libs.zxing.core)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.clientOkhttp)
            implementation(libs.kotlinx.coroutinesSwing)
            // Scanning barcodes with a webcam: openpnp-capture for the picture, ZXing to read the code.
            implementation(libs.openpnp.capture)
            implementation(libs.zxing.core)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.ktor.clientCore)
            implementation(libs.ktor.clientContentNegotiation)
            implementation(libs.ktor.serializationKotlinxJson)
            implementation(libs.ktor.clientLogging)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation(libs.kotlinx.coroutinesCore)
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
            implementation(libs.ktor.clientAuth)
            implementation(libs.voyager.navigator)
            implementation(libs.voyager.screenmodel)
            implementation(libs.voyager.transitions)
            implementation(libs.jna)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.tink.android)
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation("io.github.vinceglb:filekit-dialogs-compose:0.14.2")
        }
        jvmTest.dependencies {
            // Headless Compose screenshot tests need the desktop Skia native renderer.
            implementation(compose.desktop.currentOs)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.clientMock)
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

// Session/logout tests must never load or erase the developer's saved desktop login.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    systemProperty("user.home", layout.buildDirectory.dir("test-home").get().asFile.absolutePath)
}
