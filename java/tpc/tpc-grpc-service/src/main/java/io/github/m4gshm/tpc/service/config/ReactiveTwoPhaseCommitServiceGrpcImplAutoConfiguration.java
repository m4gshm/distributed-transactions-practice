package io.github.m4gshm.tpc.service.config;

import io.github.m4gshm.postgres.prepared.transaction.ReactivePreparedTransactionService;
import io.github.m4gshm.reactive.GrpcReactive;
import io.github.m4gshm.tpc.service.ReactiveTwoPhaseCommitServiceGrpcImpl;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static lombok.AccessLevel.PRIVATE;
import static tpc.v1.TwoPhaseCommitServiceGrpc.TwoPhaseCommitServiceImplBase;

@Configuration
@AutoConfiguration
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class ReactiveTwoPhaseCommitServiceGrpcImplAutoConfiguration {

    GrpcReactive grpc;
    ReactivePreparedTransactionService transactionService;

    @Bean
    public TwoPhaseCommitServiceImplBase twoPhaseCommitService() {
        return new ReactiveTwoPhaseCommitServiceGrpcImpl(grpc, transactionService);
    }

}
