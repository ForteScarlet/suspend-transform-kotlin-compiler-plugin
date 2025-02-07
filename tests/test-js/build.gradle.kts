import love.forte.plugin.suspendtrans.ClassInfo
import love.forte.plugin.suspendtrans.SuspendTransformConfiguration
import love.forte.plugin.suspendtrans.TargetPlatform
import love.forte.plugin.suspendtrans.gradle.SuspendTransformGradleExtension
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("multiplatform")
}


buildscript {
    this@buildscript.repositories {
        mavenLocal()
        mavenCentral()
    }
    dependencies {
        classpath("love.forte.plugin.suspend-transform:suspend-transform-plugin-gradle:2.1.0-0.9.4")
    }
}

repositories {
    mavenLocal()
}

apply(plugin = "love.forte.plugin.suspend-transform")

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes"
        )
    }

    js(IR) {
        nodejs()
        useEsModules()
        generateTypeScriptDefinitions()
        binaries.executable()

        compilerOptions {
            target = "es2015"
            useEsClasses = true
            freeCompilerArgs.addAll(
                // https://kotlinlang.org/docs/whatsnew20.html#per-file-compilation-for-kotlin-js-projects
                "-Xir-per-file",
            )
        }
    }

    sourceSets {
        named("jsMain") {
            dependencies {
                implementation(kotlin("stdlib"))
                api(libs.kotlinx.coroutines.core)
                api(project(":runtime:suspend-transform-annotation"))
                api(project(":runtime:suspend-transform-runtime"))
            }
        }
    }
}

extensions.getByType<SuspendTransformGradleExtension>().apply {
    includeRuntime = false
    includeAnnotation = false

    transformers[TargetPlatform.JS] = mutableListOf(
        SuspendTransformConfiguration.jsPromiseTransformer.copy(
            copyAnnotationExcludes = listOf(
                ClassInfo("kotlin.js", "JsExport.Ignore")
            )
        )
    )

}
