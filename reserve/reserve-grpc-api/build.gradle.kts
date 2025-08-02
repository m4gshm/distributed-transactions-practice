plugins {
    `java-library`
    id("com.google.protobuf")
}
apply(plugin = "io.spring.dependency-management")

sourceSets {
    main {
        this.proto {
            srcDirs(
                "$rootDir/proto/reserve",
                "$rootDir/proto/buf",
            )
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc"
    }
    plugins {
        this.create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java"
        }
    }
    generateProtoTasks {
        all().configureEach(Action<com.google.protobuf.gradle.GenerateProtoTask?> {
            this.plugins {
                this.create("grpc") {
                }
            }
            this.builtins {
                "java".apply {
                }
            }
        })
    }
}

dependencies {
    implementation("io.grpc:grpc-core")
    implementation("io.grpc:grpc-stub")
    implementation("io.grpc:grpc-protobuf")

    implementation("com.google.protobuf:protobuf-java")
    implementation("com.google.api.grpc:proto-google-common-protos")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
}