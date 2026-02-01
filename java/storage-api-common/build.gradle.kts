plugins {
    `java-library`
}

java {
    targetCompatibility = JavaVersion.VERSION_24
}

dependencies {
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework:spring-web")
}
