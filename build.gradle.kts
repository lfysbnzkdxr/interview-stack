// 顶层构建文件：插件声明 + Kotlin 版本绑定（AGP 9 内置 Kotlin，无需 kotlin.android 插件）
plugins {
    id("com.android.application") version "9.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("com.google.devtools.ksp") version "2.3.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10" apply false
}

// AGP 默认内置 KGP 2.2.10，项目使用 Kotlin 2.4.10 需显式声明 classpath
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}
