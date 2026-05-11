plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(21)

    jvm()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            api(libs.bignum)
        }
        jvmMain.dependencies {
            api(libs.gson)
            compileOnly(libs.slf4j.api)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test.junit5)
            implementation(libs.junit.jupiter)
        }
    }
}
