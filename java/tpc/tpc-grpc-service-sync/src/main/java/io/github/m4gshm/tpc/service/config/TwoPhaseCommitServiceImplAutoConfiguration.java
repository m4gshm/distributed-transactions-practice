package io.github.m4gshm.tpc.service.config;

import io.github.m4gshm.Grpc;
import io.github.m4gshm.postgres.prepared.transaction.PreparedTransactionService;
import io.github.m4gshm.tpc.service.TwoPhaseCommitServiceImpl;
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
public class TwoPhaseCommitServiceImplAutoConfiguration {

    @Bean
    public TwoPhaseCommitServiceImplBase twoPhaseCommitService(Grpc grpc,
                                                               PreparedTransactionService transactionService) {
        return new TwoPhaseCommitServiceImpl(grpc, transactionService);
    }

}
