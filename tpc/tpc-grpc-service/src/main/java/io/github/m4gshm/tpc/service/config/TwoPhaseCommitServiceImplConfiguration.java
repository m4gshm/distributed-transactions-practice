package io.github.m4gshm.tpc.service.config;

import io.github.m4gshm.reactive.GrpcReactive;
import org.jooq.DSLContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.github.m4gshm.tpc.service.TwoPhaseCommitServiceImpl;

import static tpc.v1.TwoPhaseCommitServiceGrpc.*;

@Configuration
@AutoConfiguration
public class TwoPhaseCommitServiceImplConfiguration {

    @Bean
    public TwoPhaseCommitServiceImplBase twoPhaseCommitService(DSLContext dsl, GrpcReactive grpc) {
        return new TwoPhaseCommitServiceImpl(dsl, grpc);
    }

}
