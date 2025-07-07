plugins {
    `java-library`
    id("com.google.protobuf")
}
apply(plugin = "io.spring.dependency-management")

sourceSets {
    main {
        this.proto {
            srcDir("$rootDir/proto/orders")
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
}