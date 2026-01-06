plugins {
    `java-library`
}

dependencies {
    api(project(":postgres-prepared-transaction"))

    api(project(":storage-api"))

    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("io.projectreactor:reactor-core")

    implementation("org.postgresql:postgresql")
}
