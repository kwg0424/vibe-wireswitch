import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.wireswitch"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.wireswitch"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        // 네이티브 라이브러리(libwg-go.so)는 ABI 마다 들어 있어 용량이 크다. 실기기(arm64)만 넣는다.
        // x86_64 에뮬레이터에서 돌리려면 "x86_64" 를 다시 추가해야 한다.
        ndk {
            abiFilters += setOf("arm64-v8a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 릴리즈 서명 — keystore.properties(gitignore)가 있을 때만 적용한다.
    val keystoreProperties = Properties()
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            // R8 코드·리소스 축소 — Compose/Kotlin 라이브러리의 안 쓰는 부분을 걷어 내 용량을 줄인다.
            optimization {
                enable = true
                // kotlin.** 을 범위에 넣으면 R8이 kotlin.collections 내부 클래스를 잘못
                // 리패키징해 IllegalAccessError 로 크래시할 수 있어 뺐다(androidx.startup 초기화 중 재현됨).
                packageScope = setOf("androidx.**", "kotlinx.**")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // WireGuard 공식 터널 라이브러리(GoBackend + VpnService + 설정 파서). 이 앱이 직접 VPN 을 연결한다.
    implementation(libs.wireguard.tunnel)
    // QR 코드 스캔 — Google Play 서비스의 스캐너 화면을 띄워 결과만 받는다(카메라 권한 불필요, 앱에 스캔 모델이 들어가지 않음).
    implementation(libs.google.code.scanner)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
