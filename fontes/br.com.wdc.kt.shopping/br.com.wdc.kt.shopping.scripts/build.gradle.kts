dependencies {
    api(project(":shopping-persistence"))
    implementation(libs.h2)
    implementation(libs.logback.classic)
}
