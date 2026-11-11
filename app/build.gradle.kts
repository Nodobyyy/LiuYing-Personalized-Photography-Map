plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "cn.photomap"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "cn.photomap"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    flavorDimensions += "mapMode"
    productFlavors {
        create("offline") {dimension="mapMode";buildConfigField("boolean","ONLINE_MAPS","false");manifestPlaceholders["appLabel"]="留影·离线"}
        create("online") {
            dimension = "mapMode"
            applicationIdSuffix = ".online"
            versionCode = 14
            versionName = "0.5.6"
            buildConfigField("boolean", "ONLINE_MAPS", "true")
            manifestPlaceholders["appLabel"] = "留影"
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += listOf("pmtiles", "pbf") }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "1g"
            if(providers.gradleProperty("includeStress").orNull!="true") it.exclude("**/LibraryStressTest*")
            if(providers.gradleProperty("useMirror").orNull=="true") it.systemProperty("robolectric.dependency.repo.url","https://maven.aliyun.com/repository/public")
        }
    }
}
dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    "offlineImplementation"("org.maplibre.gl:android-sdk:11.8.0")
    "onlineImplementation"(files(rootProject.file(".tooling/maplibre-globe/MapLibreAndroid-opengl-release-android-v2026.11.2-globe.aar")))
    "onlineImplementation"("org.maplibre.gl:android-sdk-geojson:6.0.1")
    "onlineImplementation"("org.maplibre.gl:android-sdk-turf:6.0.1")
    "onlineImplementation"("org.maplibre.gl:maplibre-android-gestures:0.0.4")
    "onlineImplementation"("androidx.fragment:fragment:1.8.9")
    "onlineImplementation"("androidx.interpolator:interpolator:1.0.0")
    "onlineImplementation"("com.jakewharton.timber:timber:5.0.1")
    "onlineImplementation"("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("io.coil-kt:coil-compose:2.7.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
}

val verifyMapBundle by tasks.registering {
    doLast {
        val bundle = file("src/offline/assets/maps")
        if (bundle.exists()) {
            exec { workingDir(rootDir); commandLine("node", "tools/stage-map.mjs", bundle.absolutePath, "--check") }
        } else error("The bundled offline map is required. Restore app/src/offline/assets/maps before building.")
    }
}
tasks.matching { it.name.startsWith("preOffline") && it.name.endsWith("Build") }
    .configureEach { dependsOn(verifyMapBundle) }
// No release artifact can accidentally be presented as a completed product.
tasks.matching { it.name.contains("Release") }.configureEach {
    doFirst { error("中国地图规范性和真机验收尚未完成，暂不生成正式发布产物；可生成 Debug APK。") }
}
