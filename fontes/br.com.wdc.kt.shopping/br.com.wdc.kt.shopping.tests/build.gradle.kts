dependencies {
    testImplementation(project(":shopping-persistence"))
    testImplementation(project(":shopping-scripts"))
    testImplementation(project(":shopping-presentation"))

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.logback.classic)
    testImplementation(libs.tomcat.dbcp)
    testImplementation(libs.h2)
}
