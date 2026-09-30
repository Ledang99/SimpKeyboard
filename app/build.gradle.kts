plugins {
    id("com.android.application")
}

android {
    namespace = "com.android.inputmethod.latin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.android.inputmethod.latin"
        minSdk = 23
        targetSdk = 36
        versionCode = 3
        versionName = "2.0.1"

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-Wall", "-Wextra")
            }
        }
    }

    buildFeatures {
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        named("main") {
            manifest.srcFile("../java/AndroidManifest.xml")
            java.setSrcDirs(listOf("../java/src"))
            res.setSrcDirs(listOf("../java/res"))
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
        disable += setOf("OldTargetApi")
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
            )
        }
    }
}
