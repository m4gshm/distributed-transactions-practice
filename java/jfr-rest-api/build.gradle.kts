plugins {
    `java-library`
}
apply(plugin = "io.spring.dependency-management")

dependencies {
    implementation("org.slf4j:slf4j-api")

    implementation("org.springframework:spring-web")
    implementation("org.springframework:spring-context")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
}
