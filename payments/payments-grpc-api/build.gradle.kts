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
        artifact = "com.google.protobuf:protoc:3.25.5"
    }
    plugins {
        this.create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.72.0"
        }
        this.create("validate") {
            artifact = "build.buf.protoc-gen-validate:protoc-gen-validate:1.2.1"
        }
    }
    generateProtoTasks {
        all().configureEach(Action<com.google.protobuf.gradle.GenerateProtoTask?> {
            this.plugins {
                this.create("grpc") {}
                this.create("validate") {
                    this.options.addAll(listOf("validate_out", "lang=java"))
                }
            }
            this.builtins {
                "java".apply {}
            }
        })
    }
}



dependencies {
    api("io.grpc:grpc-core:1.72.0")
    api("io.grpc:grpc-stub:1.72.0")
    api("io.grpc:grpc-protobuf:1.72.0")
    api("build.buf:protovalidate:0.7.2") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }
//    implementation("build.buf.protoc-gen-validate:protoc-gen-validate:1.2.1")
//    implementation("build.buf.protoc-gen-validate:pgv-java:1.2.1")
//    implementation("build.buf.protoc-gen-validate:pgv-java-validation:1.2.1")
    api("build.buf.protoc-gen-validate:pgv-java-grpc:1.2.1") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
        exclude(group = "build.buf.protoc-gen-validate", module = "pgv-java-stub")
    }
    api("build.buf.protoc-gen-validate:pgv-java-stub:1.2.1") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
        exclude(group = "com.google.protobuf", module = "protobuf-java-util")
    }

    api("com.google.protobuf:protobuf-java:3.25.8")
    api("com.google.api.grpc:proto-google-common-protos:2.59.0")
    api("javax.annotation:javax.annotation-api:1.3.2")

}