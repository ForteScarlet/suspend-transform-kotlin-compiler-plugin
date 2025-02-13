import IProject.IS_SNAPSHOT
import love.forte.gradle.common.core.project.ProjectDetail
import org.gradle.api.Project

object IProject : ProjectDetail() {
    const val IS_SNAPSHOT = false

    const val GROUP = "love.forte.plugin.suspend-transform"
    const val DESCRIPTION = "Generate platform-compatible functions for Kotlin suspend functions"
    const val HOMEPAGE = "https://github.com/ForteScarlet/kotlin-suspend-transform-compiler-plugin"

    // Remember the libs.versions.toml!
    val ktVersion = "2.1.0"
    val pluginVersion = "0.11.0"

    override val version: String = "$ktVersion-$pluginVersion"

    override val homepage: String get() = HOMEPAGE

    override val description: String get() = DESCRIPTION

    override val developers: List<Developer> = developers {
        developer {
            id = "forte"
            name = "ForteScarlet"
            email = "ForteScarlet@163.com"
            url = "https://github.com/ForteScarlet"
        }
    }
    override val group: String get() = GROUP

    override val licenses: List<License> = licenses {
        license {
            name = "MIT License"
            url = "https://mit-license.org/"
        }
    }
    override val scm: Scm = scm {
        url = HOMEPAGE
        connection = "scm:git:$HOMEPAGE.git"
        developerConnection = "scm:git:ssh://git@github.com/ForteScarlet/kotlin-suspend-transform-compiler-plugin.git"
    }

}

fun Project.setupWith(ktVersion: String) {
    group = IProject.GROUP
    description = IProject.DESCRIPTION
    val mergedVersion = ktVersion + "-" + IProject.pluginVersion
    version = if (IS_SNAPSHOT) "$mergedVersion-SNAPSHOT" else mergedVersion
}
