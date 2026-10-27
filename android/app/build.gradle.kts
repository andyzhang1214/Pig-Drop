plugins {
    id("com.android.application")
}

android {
    namespace = "com.zhuzhu.sudi"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zhuzhu.sudi"
        minSdk = 23          // Android 6.0
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        // 竖屏手机上传、横屏电视显示都在同一个包里，这里不限制方向
        resourceConfigurations += listOf("zh", "en")
    }

    /* 32 位 / 64 位各出一个包：armeabi-v7a 给老电视盒子，
       arm64-v8a 给近几年的机器。两者内核不同、系统库不通用，
       分开出包才能保证装上就能跑。 */
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
}
