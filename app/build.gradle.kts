plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pittapos.waiter"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pittapos.waiter"
        minSdk = 26
        targetSdk = 34
        versionCode = 11
        versionName = "2.0"
    }

    // Η release υπογράφεται με ΤΟ ΙΔΙΟ κλειδί που έβγαζε μέχρι τώρα η debug (το debug.keystore του
    // μηχανήματος). Χωρίς αυτό το APK θα είχε άλλη υπογραφή και τα κινητά θα ζητούσαν απεγκατάσταση
    // της παλιάς εφαρμογής — δηλαδή χαμένες ρυθμίσεις (IP, PIN) σε κάθε σερβιτόρο.
    signingConfigs {
        create("sideload") {
            storeFile = File(System.getProperty("user.home"), ".android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // Το APK που πάει στα κινητά χτίζεται πλέον σε release. Η debug έκδοση τρέχει με
            // debuggable=true, που κρατάει το ART σε πιο αργή λειτουργία — στα φθηνά κινητά φαινόταν
            // καθαρά όταν άνοιγαν τα έξτρα. Το minify μένει κλειστό: το Gson διαβάζει τα DTO με
            // reflection και ένα R8 χωρίς keep rules θα έσπαγε σιωπηλά το μενού.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.8")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-core:1.6.8")
    implementation("androidx.compose.material:material-icons-extended:1.6.8")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.8")
}
