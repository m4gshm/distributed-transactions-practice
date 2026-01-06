plugins {
    `java-library`
}

dependencies {
    api("org.slf4j:slf4j-api")
    api("org.jooq:jooq")
    implementation("org.springframework:spring-web")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
}
