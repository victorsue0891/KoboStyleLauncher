import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}
android {
    namespace = "org.kobostyle.launcher"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.kobostyle.launcher"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")
    sourceSets.getByName("debug").assets.srcDir("$projectDir/schemas")
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
// Robolectric runs the Android driver on the host; provide the matching JNI binary from
// the official desktop artifact without adding duplicate JVM classes to the test classpath.
val sqliteTestNative by configurations.creating
val extractSqliteTestNative by tasks.registering(Copy::class) {
    from({ sqliteTestNative.map { zipTree(it) } })
    include("**/sqliteJni.dll", "**/libsqliteJni.so", "**/libsqliteJni.dylib")
    into(layout.buildDirectory.dir("sqlite-test-native"))
}
tasks.withType<Test>().configureEach {
    dependsOn(extractSqliteTestNative)
    doFirst {
        val files = fileTree(layout.buildDirectory.dir("sqlite-test-native")).files
        val os = System.getProperty("os.name").lowercase().let { if (it.contains("windows")) "windows" else if (it.contains("mac")) "osx" else "linux" }
        val arch = if (System.getProperty("os.arch").lowercase() in listOf("aarch64", "arm64")) "arm64" else "x64"
        systemProperty("java.library.path", files.filter { it.parentFile.name == "${os}_$arch" }.map { it.parentFile.absolutePath }.distinct().joinToString(File.pathSeparator))
    }
}
dependencies {
    sqliteTestNative("androidx.sqlite:sqlite-bundled-jvm:2.6.2@jar")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.sqlite:sqlite-bundled:2.6.2")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
}
