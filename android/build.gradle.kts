plugins {
    // AGP 9 起 Kotlin 支持内置在 Android 插件里，不再需要 org.jetbrains.kotlin.android。
    // 两个插件同时挂会报 "Cannot add extension with name 'kotlin'"。
    id("com.android.application") version "9.3.1" apply false
}
