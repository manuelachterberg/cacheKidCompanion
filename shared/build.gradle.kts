plugins {
    kotlin("multiplatform")
    id("com.android.library")
    kotlin("plugin.serialization") version "2.2.10"
}

kotlin {
    androidTarget()
    
    iosX64 {
        binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }
    iosArm64 {
        binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.cachekid.companion.shared"
    compileSdk = 34
    
    defaultConfig {
        minSdk = 26
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
