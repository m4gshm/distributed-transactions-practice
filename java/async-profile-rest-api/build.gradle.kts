plugins {
    `java-library`
}
apply(plugin = "io.spring.dependency-management")

dependencies {
    implementation("org.slf4j:slf4j-api")

    implementation("org.springframework:spring-web")
    implementation("org.springframework:spring-context")

    api("tools.profiler:async-profiler:4.3")
}
