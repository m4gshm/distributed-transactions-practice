plugins {
    `java-library`
    id("com.google.protobuf")
    id("org.springframework.boot")
}
apply(plugin = "io.spring.dependency-management")

sourceSets {
    main {
        proto {
            srcDirs("$rootDir/proto/orders")
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.5"
    }
    plugins {
        this.create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.72.0"
        }
    }
    generateProtoTasks {
        all().configureEach(Action<com.google.protobuf.gradle.GenerateProtoTask?> {
            this.plugins {
                this.create("grpc") {}
            }
            this.builtins {
                "java".apply {
                }
            }
        })
    }
}

dependencies {
    implementation("io.grpc:grpc-core:1.72.0")
    implementation("io.grpc:grpc-stub:1.72.0")
    implementation("io.grpc:grpc-protobuf:1.72.0")
    implementation("com.google.protobuf:protobuf-java:3.25.5")
    implementation("com.google.api.grpc:proto-google-common-protos:2.59.0")
    implementation("javax.annotation:javax.annotation-api:1.3.2")

    implementation("org.springframework.boot:spring-boot-starter-web")
//    implementation("org.springframework.grpc:spring-grpc-spring-boot-starter")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")

    implementation("io.grpc:grpc-netty-shaded:1.73.0")

    implementation(platform("io.github.danielliu1123:grpc-starter-dependencies:3.5.3.1"))
    implementation("io.github.danielliu1123:grpc-server-boot-starter")
    implementation("io.github.danielliu1123:grpc-starter-transcoding")
    implementation("io.github.danielliu1123:grpc-starter-transcoding-springdoc")


    implementation("org.springframework.boot:spring-boot-autoconfigure")

//    implementation(platform("org.springframework.grpc:spring-grpc-dependencies:0.9.0"))

    modules {
        module("io.grpc:grpc-netty") {
            replacedBy("io.grpc:grpc-netty-shaded", "Use Netty shaded instead of regular Netty")
        }
    }
}