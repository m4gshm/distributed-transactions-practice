plugins {
    `java-library`
}

dependencies {
    implementation("org.slf4j:slf4j-api")
    implementation("org.postgresql:r2dbc-postgresql")
    implementation("org.springframework:spring-context")
    implementation("org.springframework.boot:spring-boot-r2dbc")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
}
