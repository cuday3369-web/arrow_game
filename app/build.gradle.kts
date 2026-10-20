plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yogi.exitarrow"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yogi.exitarrow"
        minSdk = 26
        targetSdk = 35

        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.dynamicanimation)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.graphics.core)
    implementation(libs.androidx.palette)
    implementation(libs.lottie)
    testImplementation("junit:junit:4.13.2")
}
