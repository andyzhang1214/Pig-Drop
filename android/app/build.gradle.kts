import com.android.build.api.variant.FilterConfiguration
import java.util.Properties

plugins {
    id("com.android.application")
}

/* 版本号与产物名前缀集中在这里，避免 defaultConfig 和输出文件名各写一份导致对不上。
   产物名形如 PigDrop-1.0.0-arm64-v8a.apk，跟 GitHub Release 上的附件名一致。 */
val appVersionCode = 1
val appVersionName = "1.0.0"
val apkBaseName = "PigDrop"

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
        versionCode = appVersionCode
        versionName = appVersionName

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

/* 输出文件名：默认是 app-arm64-v8a-release.apk，跟发布页附件名对不上，
   每次发版都要手工改名，容易漏。这里直接按 <品牌>-<版本>-<ABI>.apk 命名。
   debug 包额外带 -debug，避免和 release 包看混。 */
androidComponents {
    onVariants { variant ->
        val debugTag = if (variant.buildType == "debug") "-debug" else ""
        variant.outputs.forEach { output ->
            val abi = output.filters
                .firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier
                ?: "universal"
            output.outputFileName.set("$apkBaseName-$appVersionName-$abi$debugTag.apk")
        }
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
