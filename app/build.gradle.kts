plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "vn.edu.ueh.thudinh.fashion2hand"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "vn.edu.ueh.thudinh.fashion2hand"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
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
    implementation(libs.constraintlayout)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.firestore)
    implementation("com.google.firebase:firebase-storage:22.0.2")
    implementation(libs.googleid)
    implementation(libs.material)
    testImplementation(libs.junit)
    implementation("de.hdodenhof:circleimageview:3.1.0")
    // Khu vực Admin: RecyclerView, Fragment, ViewModel
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.fragment:fragment:1.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
