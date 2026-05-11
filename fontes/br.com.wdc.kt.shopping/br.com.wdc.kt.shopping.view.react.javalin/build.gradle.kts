plugins {
    application
}

application {
    mainClass.set("br.com.wdc.shopping.view.react.JavalinApplication")
}

dependencies {
    api(project(":view-react-skeleton"))
    api(project(":shopping-scripts"))
    api(project(":shopping-persistence"))
    api(project(":shopping-domain"))
    api(project(":framework-commons"))
    api(libs.javalin)
    api(libs.gson)
    implementation(libs.h2)
    implementation(libs.logback.classic)
    implementation(libs.slf4j.api)
}
