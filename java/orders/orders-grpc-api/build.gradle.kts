plugins {
    `java-library`
    id("com.google.protobuf")
}
apply(plugin = "io.spring.dependency-management")

sourceSets {
    main {
        this.proto {
            exclude("buf/**", "google/**")
            setIncludes(listOf(
                "order/**/*.proto",
                "payment/v1/model/*.proto",
                "reserve/v1/model/*.proto",
                ))
            srcDirs("$rootDir/../proto")
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java"
        }
    }
    generateProtoTasks {
        all().configureEach(Action<com.google.protobuf.gradle.GenerateProtoTask> {
            plugins {
                create("grpc") {}
            }
            builtins.maybeCreate("java").apply {
//                option("lite")
//                option("shared=true")
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
