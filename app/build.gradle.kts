plugins { id("com.android.application"); kotlin("android"); kotlin("plugin.serialization"); id("org.jetbrains.kotlin.plugin.compose"); id("com.google.devtools.ksp"); id("com.google.dagger.hilt.android") }
android {
    namespace = "app.tormang"
    compileSdk = 35
    defaultConfig { minSdk = 26; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"; applicationId = "app.tormang"; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isIncludeAndroidResources = true }
    buildFeatures { compose = true }
}
dependencies {
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    implementation(project(":domain"))
    implementation(project(":core:common"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
implementation(project(":data"))
 implementation(project(":core:preferences"))
 implementation(project(":core:designsystem"))
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.navigation:navigation-compose:2.8.5")
 implementation("androidx.work:work-runtime-ktx:2.10.0")
 implementation("androidx.hilt:hilt-work:1.2.0")
 implementation("io.coil-kt:coil:2.7.0")
 androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
 androidTestImplementation("androidx.compose.ui:ui-test-junit4")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
 androidTestImplementation("androidx.test:runner:1.6.2")
 debugImplementation("androidx.compose.ui:ui-test-manifest")
implementation(project(":feature:library"))
implementation(project(":feature:browse"))
implementation(project(":feature:manga"))
implementation(project(":feature:reader"))
implementation(project(":feature:downloads"))
implementation(project(":feature:history"))
implementation(project(":feature:updates"))
implementation(project(":feature:settings"))
implementation(project(":feature:tracking"))
implementation(project(":feature:backup"))
}
