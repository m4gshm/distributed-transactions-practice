plugins {
    `java-library`
}

dependencies {
    implementation("jakarta.validation:jakarta.validation-api")

    implementation("org.slf4j:slf4j-api")
    implementation("io.projectreactor:reactor-core")
    implementation("org.springframework:spring-web")
}
