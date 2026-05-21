dependencies {
    testImplementation(project(":shopping-persistence"))
    testImplementation(project(":shopping-persistence-client"))
    testImplementation(project(":persistence-rest"))
    testImplementation(project(":shopping-scripts"))
    testImplementation(project(":shopping-presentation"))

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.logback.classic)
    testImplementation(libs.tomcat.dbcp)
    testImplementation(libs.h2)
    testImplementation(libs.javalin)
    testImplementation(libs.gson)
    testImplementation(libs.okhttp)
}
