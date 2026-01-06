plugins {
    `java-library`
}

dependencies {
    api(project(":jooq"))

    implementation("jakarta.validation:jakarta.validation-api")

    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework:spring-web")
}
