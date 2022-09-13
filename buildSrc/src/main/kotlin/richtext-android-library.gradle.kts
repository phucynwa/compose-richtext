plugins {
  id("com.android.library")
  kotlin("android")
  id("maven-publish")
  id("signing")
}

kotlin {
  explicitApi()
}

android {
  compileSdk = AndroidConfiguration.compileSdk

  defaultConfig {
    minSdk = AndroidConfiguration.minSdk
    targetSdk = AndroidConfiguration.targetSdk
  }

  kotlinOptions { jvmTarget = "11" }

  buildFeatures {
    compose = true
  }

  composeOptions {
    kotlinCompilerExtensionVersion = Compose.compilerVersion
  }
}
