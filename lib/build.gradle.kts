// The reusable half: support detection, the accessibility toggle, the boot
// record and the documented API. Everything under src/main/res is the
// accessibility service's metadata, which a consumer inherits from the AAR.
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "io.github.mbanucu.blackviewautostart"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
    }

    buildFeatures {
        buildConfig = false
    }
}

group = "io.github.mbanucu"
version = providers.gradleProperty("version").getOrElse(libs.versions.projectVersion.get())

publishing {
    repositories {
        maven {
            name = "outRepo"
            url = uri(layout.buildDirectory.dir("m2repo"))
        }
    }

    publications {
        create<MavenPublication>("release") {
            // The module is :lib, but the published coordinate is the library's
            // name; without this the artifact would be io.github.mbanucu:lib.
            artifactId = "blackview-autostart"
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("blackview-autostart")
                description.set(
                    "DokeOS autostart allowlisting: support detection, a label-driven " +
                        "accessibility toggle, and a boot record."
                )
                url.set("https://github.com/MBanucu/blackview-autostart")
                licenses {
                    license {
                        name.set("GNU General Public License v3.0 or later")
                        url.set("https://www.gnu.org/licenses/gpl-3.0-standalone.html")
                    }
                }
                developers {
                    developer {
                        id.set("MBanucu")
                        name.set("MBanucu")
                        url.set("https://github.com/MBanucu")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/MBanucu/blackview-autostart.git")
                    developerConnection.set(
                        "scm:git:ssh://git@github.com/MBanucu/blackview-autostart.git"
                    )
                    url.set("https://github.com/MBanucu/blackview-autostart")
                }
            }
        }
    }
}
