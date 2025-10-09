plugins {
    `java-library`
}
apply(plugin = "io.spring.dependency-management")

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
}

