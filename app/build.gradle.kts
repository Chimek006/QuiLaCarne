import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}
val baseUrl = localProperties.getProperty("BASE_URL") ?: "https://api.quilacarne.com.pl/api/"

android {
    namespace = "com.example.quilacarne"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.quilacarne"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kover {
    reports {
        filters {
            includes {
                classes(
                    "com.example.quilacarne.data.local.TokenManager",
                    "com.example.quilacarne.data.remote.network.RemoteTokenStore",
                    "com.example.quilacarne.data.remote.network.NetworkIssueResolver",
                    "com.example.quilacarne.data.repository.TableStatusSyncLogic",
                    "com.example.quilacarne.data.repository.ApiResponseLogic",
                    "com.example.quilacarne.utils.ReservationTimeUtils"
                )
            }
            excludes {
                classes(
                    "*.BuildConfig",
                    "*.MainActivity",
                    "*ComposableSingletons*",
                    "*Kt\$*",
                    "com.example.quilacarne.data.repository.AuthRepository",
                    "com.example.quilacarne.data.repository.OrderRepository",
                    "com.example.quilacarne.data.repository.SyncRepository",
                    "com.example.quilacarne.data.remote.api.*",
                    "com.example.quilacarne.data.remote.dto.*",
                    "com.example.quilacarne.data.remote.network.AuthInterceptor",
                    "com.example.quilacarne.data.remote.network.ApiLoggingInterceptor",
                    "com.example.quilacarne.data.remote.network.RetrofitClient",
                    "com.example.quilacarne.data.remote.network.TokenAuthenticator",
                    "com.example.quilacarne.data.remote.network.NetworkMonitor",
                    "com.example.quilacarne.data.local.AppDatabase",
                    "com.example.quilacarne.data.local.Converters",
                    "com.example.quilacarne.data.local.entities.*",
                    "com.example.quilacarne.data.local.relations.*",
                    "com.example.quilacarne.ui.*"
                )
                packages(
                    "com.example.quilacarne.ui",
                    "com.example.quilacarne.data.remote.api",
                    "com.example.quilacarne.data.remote.dto"
                )
                annotatedBy(
                    "androidx.compose.runtime.Composable",
                    "androidx.compose.ui.tooling.preview.Preview"
                )
            }
        }

        verify {
            rule {
                minBound(80)
            }
        }
    }
}

detekt {
    toolVersion = libs.versions.detekt.get()
    source.setFrom("src/main/java", "src/test/java")
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    baseline = rootProject.file("config/detekt/baseline.xml")
    buildUponDefaultConfig = true
    allRules = false
    parallel = true
    ignoreFailures = false
    basePath = rootProject.projectDir.absolutePath
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = JavaVersion.VERSION_11.toString()
    exclude("**/build/**", "**/generated/**")

    reports {
        html.required.set(true)
        xml.required.set(true)
        sarif.required.set(true)
        md.required.set(true)
    }
}

tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget = JavaVersion.VERSION_11.toString()
    exclude("**/build/**", "**/generated/**")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation("androidx.navigation:navigation-compose:2.8.5")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    val retrofitVersion = "2.11.0"
    implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-gson:$retrofitVersion")

    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("androidx.security:security-crypto:1.1.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite:2.4.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    implementation("io.coil-kt:coil-compose:2.5.0")

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
