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
  // AndroidX Media / Media3
  implementation(files("libs/lib-ui-release.aar"))
  implementation(files("libs/lib-common-release.aar"))
  implementation(files("libs/lib-container-release.aar"))
  implementation(files("libs/lib-datasource-release.aar"))
  implementation(files("libs/lib-database-release.aar"))
  implementation(files("libs/lib-decoder-release.aar"))
  implementation(files("libs/lib-extractor-release.aar"))
  implementation(files("libs/lib-exoplayer-release.aar"))
  implementation(files("libs/lib-exoplayer-rtsp-release.aar"))
  
  implementation("com.google.guava:guava:33.7.1-android")
  implementation("androidx.recyclerview:recyclerview:1.4.0")

  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.constraintlayout)
}