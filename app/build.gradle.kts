plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "com.example.a2uie2e"
  compileSdk = 37
  compileSdkMinor = 1

  defaultConfig {
    applicationId = "com.example.a2uie2e"
    minSdk = 24
    targetSdk = 37
    versionCode = 1
    versionName = "1.0"
  }
  buildFeatures { compose = true }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  implementation(platform("androidx.compose:compose-bom:2026.09.00"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.activity:activity-compose:1.12.0")

  // A2UI: data layer + Compose renderer + ready-made Material 3 Basic Catalog
  implementation("androidx.a2ui:a2ui-model:1.0.0-alpha01")
  implementation("androidx.a2ui:a2ui-engine:1.0.0-alpha01")
  implementation("androidx.a2ui.compose:compose-runtime:1.0.0-alpha01")
  implementation("androidx.a2ui.compose:compose-ui:1.0.0-alpha01")
  implementation("androidx.compose.material3:material3-a2ui:1.0.0-alpha01")
}
