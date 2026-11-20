import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.forgeprom.studio"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.forgeprom.studio"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.4.0"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // توقيع الإصدار: keystore.properties محلي أو متغيرات بيئة في CI. غيابهما يعني APK غير موقّع.
    val keystoreFile = rootProject.file("keystore.properties")
    val keystore = Properties().apply {
        if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) }
    }
    fun secret(key: String, env: String): String? =
        keystore.getProperty(key) ?: System.getenv(env)

    signingConfigs {
        create("release") {
            val path = secret("storeFile", "FORGEPROM_KEYSTORE")
            if (path != null) {
                storeFile = rootProject.file(path)
                storePassword = secret("storePassword", "FORGEPROM_STORE_PASSWORD")
                keyAlias = secret("keyAlias", "FORGEPROM_KEY_ALIAS")
                keyPassword = secret("keyPassword", "FORGEPROM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release").takeIf {
                secret("storeFile", "FORGEPROM_KEYSTORE") != null
            }
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // R16 — متابعة مهام الطابور في الخلفية بعد إغلاق التطبيق
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // R17 — تحسين بدء التشغيل (install-time profiles) دون ملف baseline مولّد على جهاز
    implementation("androidx.profileinstaller:profileinstaller:1.4.0")

    // R2 — قاعدة البيانات المحلية
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // R11 — حماية مفاتيح API
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // R4/R8 — عملاء الشبكة للمزودين السحابيين (اختياري، بموافقة)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.google.mediapipe:tasks-genai:0.10.24")
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // R12 — اختبارات وحدة لطبقة المجال
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // R17 — اختبارات Compose تعمل على JVM عبر Robolectric (CI بلا محاكي)
    testImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit-ktx:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.compose.ui:ui-test-manifest")
}
