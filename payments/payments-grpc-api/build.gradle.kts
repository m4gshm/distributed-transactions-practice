plugins {
    `java-library`
    id("com.google.protobuf")
}
apply(plugin = "io.spring.dependency-management")

sourceSets {
    main {
        this.proto {
            srcDirs(
                "$rootDir/proto/payment",
                "$rootDir/proto/tpc"
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
                this.create("grpc") {}
            }
            this.builtins {
                "java".apply {}
            }
        })
    }
}

dependencies {
    api("io.grpc:grpc-core")
    api("io.grpc:grpc-stub")
    api("io.grpc:grpc-protobuf")

    api("build.buf:protovalidate")
    api("com.google.protobuf:protobuf-java")
    api("com.google.api.grpc:proto-google-common-protos")
    api("javax.annotation:javax.annotation-api")
}