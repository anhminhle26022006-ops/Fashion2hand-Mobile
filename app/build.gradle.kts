plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    buildFeatures { buildConfig = true }
    namespace = "vn.edu.ueh.thudinh.fashion2hand"
    compileSdk = 36

    defaultConfig {
        applicationId = "vn.edu.ueh.thudinh.fashion2hand"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "vn.edu.ueh.thudinh.fashion2hand.EmulatorTestRunner"
        buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"${providers.gradleProperty("firebaseEmulatorHost").orElse("10.0.2.2").get()}\"")
    }

    buildTypes {
        release {
            buildConfigField("boolean", "USE_FIREBASE_EMULATORS", "false")
            isMinifyEnabled = false
        }
        debug {
            buildConfigField("boolean", "USE_FIREBASE_EMULATORS",
                providers.gradleProperty("useFirebaseEmulators").orElse("false").get().toBoolean().toString())
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation("androidx.browser:browser:1.8.0")
    implementation(libs.constraintlayout)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)

    // Firebase BoM quản lý chung phiên bản
    implementation(platform("com.google.firebase:firebase-bom:33.0.0"))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // Khai báo trực tiếp không qua catalog để tránh lỗi Unresolved reference
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-functions")

    implementation(libs.googleid)
    implementation(libs.material)
    testImplementation(libs.junit)
    implementation("de.hdodenhof:circleimageview:3.1.0")

    // Khu vực Admin
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.fragment:fragment:1.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.7")

    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
