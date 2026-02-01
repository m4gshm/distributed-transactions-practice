plugins {
//    `java-library`
    kotlin("jvm") version "2.2.21"
}
apply(plugin = "io.spring.dependency-management")

java {
    targetCompatibility = JavaVersion.VERSION_24
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
}

dependencies {
    api(project(":storage-api"))
    api(project(":orders:orders-storage-jooq-api"))
    compileOnly(project(":orders:orders-storage-jooq-jdbc"))
    api("org.springframework.boot:spring-boot-starter-jooq")
    implementation("io.opentelemetry:opentelemetry-context")
}
