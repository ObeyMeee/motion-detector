plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("de.undercouch.download")
}

android {
    namespace = "ua.com.andromeda.motiondetector"
    compileSdk = 34

    defaultConfig {
        applicationId = "ua.com.andromeda.motiondetector"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/native-image/**"
        }
    }
}

// import DownloadMPTasks task
project.ext["ASSET_DIR"] = "$projectDir/src/main/assets"
apply {
    from("download_task.gradle")
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)


    implementation("com.google.mediapipe:tasks-vision:0.10.14")
    implementation(libs.pose.detection.common)
    implementation("org.bytedeco:javacv-platform:1.5.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")

    // CameraX
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:${cameraxVersion}")
    implementation("androidx.camera:camera-camera2:${cameraxVersion}")
    implementation("androidx.camera:camera-view:${cameraxVersion}")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")


    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}