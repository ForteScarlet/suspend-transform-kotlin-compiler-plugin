import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.github.gmazzo.buildconfig")
    id("suspend-transform.jvm-maven-publish")
}

//testWithEmbedded0()


dependencies {
    compileOnly(kotlin("stdlib"))
    implementation(kotlin("compiler"))
    compileOnly(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    // TODO 改成二进制的，比如 protobuf

    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("stdlib"))
    testImplementation(kotlin("test-junit5"))

    testImplementation(kotlin("compiler"))
    testImplementation(kotlin("reflect"))
    // see https://github.com/Icyrockton/xjson
    testImplementation(kotlin("compiler-internal-test-framework"))  // compiler plugin test generator / test utils
    testRuntimeOnly(kotlin("test"))
    testRuntimeOnly(kotlin("script-runtime"))
    testRuntimeOnly(kotlin("annotations-jvm"))
    testImplementation(project(":runtime:suspend-transform-annotation"))
    testImplementation(project(":runtime:suspend-transform-runtime"))

//    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.4.9")
//    testImplementation("org.bitbucket.mstrobel:procyon-compilertools:0.6.0")

    testImplementation(libs.kotlinx.coroutines.core)
}

//val compileKotlin: KotlinCompile by tasks
//compileKotlin.kotlinOptions.freeCompilerArgs += listOf(
//    "-Xjvm-default=all",
//    "-opt-in=kotlin.RequiresOptIn",
//    "-opt-in=org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI",
//)

//tasks.withType<KotlinCompile> {
//    kotlinOptions.jvmTarget = "1.8"
//}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        optIn.addAll(
            "kotlin.RequiresOptIn",
            "org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI"
        )
        freeCompilerArgs.addAll(
            "-Xjvm-default=all",
//            "-opt-in=kotlin.RequiresOptIn",
//            "-opt-in=org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI",
        )
    }
}

tasks.withType(KotlinCompile::class.java).configureEach {
    // see https://youtrack.jetbrains.com/issue/KTIJ-21563
    // see https://youtrack.jetbrains.com/issue/KT-57297
//    kotlinOptions {
//        languageVersion = "1.9"
//        apiVersion = "1.9"
//    }
}

repositories {
    maven {
        url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        mavenContent {
            snapshotsOnly()
        }
    }
}

buildConfig {
    useKotlinOutput {
        internalVisibility = true
    }
    withoutPackage()
    buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${rootProject.extra["kotlin_plugin_id"]}\"")
}




sourceSets {
    test{
        kotlin.srcDir("src/test")
        kotlin.srcDir("src/test-gen")
        java.srcDir("src/test-gen")
    }
}

task<JavaExec>("generateTest") {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "love.forte.plugin.suspendtrans.GenerateTestsKt"
}

// add following properties for test
tasks.test {
    useJUnitPlatform()
    doFirst {
        setLibraryProperty("org.jetbrains.kotlin.test.kotlin-stdlib", "kotlin-stdlib")
        setLibraryProperty("org.jetbrains.kotlin.test.kotlin-stdlib-jdk8", "kotlin-stdlib-jdk8")
        setLibraryProperty("org.jetbrains.kotlin.test.kotlin-reflect", "kotlin-reflect")
        setLibraryProperty("org.jetbrains.kotlin.test.kotlin-test", "kotlin-test")
        setLibraryProperty("org.jetbrains.kotlin.test.kotlin-script-runtime", "kotlin-script-runtime")
        setLibraryProperty("org.jetbrains.kotlin.test.kotlin-annotations-jvm", "kotlin-annotations-jvm")
    }
}

fun Test.setLibraryProperty(propName: String, jarName: String) {
    val path = project.configurations
        .testRuntimeClasspath.get()
        .files
        .find { """$jarName-\d.*jar""".toRegex().matches(it.name) }
        ?.absolutePath
        ?: return
    systemProperty(propName, path)
}

/*
上面与测试相关的一些内容参考自 https://github.com/Icyrockton/xjson
 */
