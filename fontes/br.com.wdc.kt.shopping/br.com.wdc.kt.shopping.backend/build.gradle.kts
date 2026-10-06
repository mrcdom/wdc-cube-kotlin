plugins {
    application
}

application {
    mainClass.set("br.com.wdc.shopping.view.react.JavalinApplication")
}

// O backend roda a partir de fontes/: é lá que fica work/ (configuração, banco, logs e os frontends publicados).
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

dependencies {
    api(project(":view-remote-react-skeleton"))
    api(project(":persistence-rest"))
    api(project(":shopping-scripts"))
    api(project(":shopping-persistence"))
    api(project(":shopping-domain"))
    api(project(":framework-commons"))
    api(libs.javalin)
    api(libs.gson)
    implementation(libs.h2)
    implementation(libs.postgresql)
    implementation(libs.agroal.pool)
    implementation(libs.logback.classic)
    implementation(libs.slf4j.api)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.embedded.postgres)
}
