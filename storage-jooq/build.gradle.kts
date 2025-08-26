plugins {
    `java-library`
}

dependencies {
    api(project(":storage-api"))

    implementation("org.slf4j:slf4j-api")
    implementation("org.jooq:jooq:3.19.24")
    implementation("io.projectreactor:reactor-core")
}
