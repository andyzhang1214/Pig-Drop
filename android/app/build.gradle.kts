import java.util.Properties

plugins {
    id("com.android.application")
}

/* 发布签名不写进仓库：keystore.properties 与 .jks 都在 .gitignore 里。
   文件缺失时 release 回落到 debug 签名，保证任何人克隆下来都能 assembleRelease。 */
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKey = keystoreProps.getProperty("storeFile") != null

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

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // 没配密钥时退回 debug 签名，产物仍可安装（只是签名不同）
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
