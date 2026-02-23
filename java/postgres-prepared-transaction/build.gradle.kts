plugins {
    `java-library`
}

dependencies {
    api(project(":storage-api"))
    implementation("org.slf4j:slf4j-api")
}
