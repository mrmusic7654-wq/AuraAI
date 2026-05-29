package com.aura.ai.services

object BuildTemplates {
    
    fun rootBuildGradle() = """
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
}
""".trimIndent()

    fun appBuildGradle(packageName: String, deps: String = "") = """
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "$packageName"
    compileSdk = 34
    
    defaultConfig {
        applicationId = "$packageName"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.10" }
    kotlinOptions { jvmTarget = "17" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.navigation:navigation-compose:2.7.6")
    $deps
}
""".trimIndent()

    fun settingsGradle(name: String) = """
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "$name"
include(":app")
""".trimIndent()

    fun gradleProperties() = """
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
""".trimIndent()

    fun wrapperProperties() = """
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.4-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
""".trimIndent()

    fun androidManifest(packageName: String, appName: String, perms: String = "") = """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
    $perms
    
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="$appName"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.$appName">
        
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.$appName">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
    </application>
</manifest>
""".trimIndent()

    fun themeXml(appName: String) = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.$appName" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:statusBarColor">@color/black</item>
    </style>
</resources>"""

    fun colorsXml() = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="purple_200">#FFBB86FC</color>
    <color name="purple_500">#FF6200EE</color>
    <color name="purple_700">#FF3700B3</color>
    <color name="teal_200">#FF03DAC5</color>
    <color name="teal_700">#FF018786</color>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>
</resources>"""

    fun stringsXml(appName: String) = """<resources>
    <string name="app_name">$appName</string>
</resources>"""

    fun workflowYaml(appName: String) = """
name: Build $appName

on:
  push:
    branches: [ main, master ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup Gradle Wrapper
        run: |
          if [ ! -f "gradlew" ]; then
            gradle wrapper --gradle-version 8.4
          fi
          chmod +x gradlew
      
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - uses: gradle/actions/setup-gradle@v3
      
      - name: Build Debug APK
        run: ./gradlew assembleDebug --no-daemon
        env:
          GRADLE_OPTS: "-Dorg.gradle.jvmargs=-Xmx4g"
      
      - name: Upload APK Artifact
        uses: actions/upload-artifact@v4
        with:
          name: ${appName}-debug
          path: app/build/outputs/apk/debug/app-debug.apk
""".trimIndent()

    fun generateCoreFiles(
        appName: String,
        packageName: String,
        deps: String = "",
        perms: String = ""
    ): Map<String, String> = mapOf(
        "build.gradle.kts" to rootBuildGradle(),
        "app/build.gradle.kts" to appBuildGradle(packageName, deps),
        "settings.gradle.kts" to settingsGradle(appName),
        "gradle.properties" to gradleProperties(),
        "gradle/wrapper/gradle-wrapper.properties" to wrapperProperties(),
        "app/src/main/AndroidManifest.xml" to androidManifest(packageName, appName, perms),
        "app/src/main/res/values/themes.xml" to themeXml(appName),
        "app/src/main/res/values/colors.xml" to colorsXml(),
        "app/src/main/res/values/strings.xml" to stringsXml(appName)
    )
}
