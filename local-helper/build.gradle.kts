import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    kotlin("multiplatform")
//    id("love.forte.plugin.suspend-transform")
    // id("suspend-transform.jvm-maven-publish")
    // id(project(":suspend-transform-plugin-gradle"))
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(8)

    jvm {
        compilerOptions {
            freeCompilerArgs.add("-Xjvm-default=all")
            jvmTarget = JvmTarget.JVM_1_8
        }

        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    js {
        nodejs()
        compilerOptions {
            useEsModules()
            // https://kotlinlang.org/docs/js-ir-compiler.html#output-mode
            freeCompilerArgs.add("-Xir-per-file")
        }
        generateTypeScriptDefinitions()
        binaries.library()
    }

    macosX64()
    linuxX64()
    mingwX64()

    sourceSets {
        commonMain {
            dependencies {
//    api(kotlin("reflect"))
                api(libs.kotlinx.coroutines.core)
            }
        }

        jvmTest {
            dependencies {
                api(kotlin("test-junit5"))
            }
        }
    }
}

