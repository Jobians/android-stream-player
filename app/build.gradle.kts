plugins {
  id("com.android.application")
}

android {
  compileSdk = 37
  buildToolsVersion = "37.0.0"
  namespace = "dev.jt.streamplayer"

  defaultConfig {
    minSdk = 26
    targetSdk = 37
    versionCode = 1
    versionName = "0.0.1"
    applicationId = "dev.jt.streamplayer"
  }

  signingConfigs {
    getByName("debug") {
      val customKeystore = file("../keystore/debug.keystore")
      if (customKeystore.exists()) {
        storeFile = customKeystore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      signingConfig = signingConfigs.getByName("debug")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
  }
}

dependencies {
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.constraintlayout)

  // AndroidX Media3
  implementation(fileTree("libs") {
    include("*.aar")
  })

  implementation("com.google.guava:guava:33.7.1-android")
  implementation("androidx.recyclerview:recyclerview:1.4.0")
}