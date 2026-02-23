plugins {
    `java-library`
}
apply(plugin = "io.spring.dependency-management")

dependencies {
    api(project(":storage-api"))
    api(project(":reserve:reserve-storage-jooq"))

    implementation("jakarta.validation:jakarta.validation-api")

    implementation("org.postgresql:postgresql")

    implementation("org.springframework.boot:spring-boot-starter-jooq")

}
