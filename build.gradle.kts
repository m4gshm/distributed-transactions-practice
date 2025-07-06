plugins {
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.protobuf") version "0.9.5" apply false
    id("org.springframework.boot") version "3.5.3" apply false
}

allprojects {
    repositories {
        mavenCentral()
    }
}
