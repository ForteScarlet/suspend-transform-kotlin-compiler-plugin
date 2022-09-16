plugins {
    `java-library`
    kotlin("jvm")
    id("love.forte.plugin.suspend-transform")
    id("suspend-transform.jvm-maven-publish")
    // id(project(":suspend-transform-plugin-gradle"))
}

// buildscript {
//     this@buildscript.repositories {
//         mavenLocal()
//         mavenCentral()
//     }
//     dependencies {
//         classpath("love.forte.plugin.suspend-transform:suspend-transform-plugin-gradle:0.0.1")
//     }
// }

//withType<JavaCompile> {
//    sourceCompatibility = "11"
//    targetCompatibility = "11"
//}
//withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
//    kotlinOptions.jvmTarget = "11"
//}





kotlin {
    dependencies {
        api(kotlin("stdlib"))
        api("love.forte.plugin.suspend-transform:suspend-transform-runtime:0.0.1")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    }
}