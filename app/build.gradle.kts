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
  implementation(libs.media3.ui)
  implementation(libs.media3.exoplayer)
  implementation(libs.androidx.appcompat)
  implementation(libs.media3.exoplayer.rtsp)
  implementation(libs.androidx.constraintlayout)
}