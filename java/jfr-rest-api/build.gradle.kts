import com.bmuschko.gradle.docker.DockerConventionJvmApplicationPlugin
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

plugins {
    `java-library`
}
apply(plugin = "io.spring.dependency-management")

dependencies {
    implementation("org.springframework:spring-web")
    implementation("org.springframework:spring-context")
}
