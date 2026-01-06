plugins {
    `java-library`
}

dependencies {
    api(project(":storage-reactive-api"))

    implementation("org.slf4j:slf4j-api")
    implementation("org.jooq:jooq")
    implementation("io.projectreactor:reactor-core")
}
