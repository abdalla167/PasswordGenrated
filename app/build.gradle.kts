import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "com.geidea.passwordgenrated"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.geidea.passwordgenrated"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val storeFilePath = keystoreProperties.getProperty("storeFile")
        val storePassword = keystoreProperties.getProperty("storePassword")
        val keyAlias = keystoreProperties.getProperty("keyAlias")
        val keyPassword = keystoreProperties.getProperty("keyPassword")
        if (
            keystorePropertiesFile.exists() &&
                !storeFilePath.isNullOrBlank() &&
                !storePassword.isNullOrBlank() &&
                !keyAlias.isNullOrBlank() &&
                !keyPassword.isNullOrBlank()
        ) {
            val storeFile = rootProject.file(storeFilePath)
            if (storeFile.isFile) {
                create("release") {
                    this.storeFile = storeFile
                    this.storePassword = storePassword
                    this.keyAlias = keyAlias
                    this.keyPassword = keyPassword
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig =
                signingConfigs.findByName("release")
                    ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(
        fileTree("libs") {
            include("*.jar", "*.aar")
            exclude("*-sources.jar")
        }
    )
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}