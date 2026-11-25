// A demo app with no dependencies beyond the library. It is the manual
// acceptance test: enable the accessibility service, then toggle a row.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.mbanucu.blackviewautostart.sample"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        applicationId = "io.github.mbanucu.blackviewautostart.sample"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = libs.versions.projectVersion.get()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }

    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    implementation(project(":lib"))
}
