package payments.data;

import io.github.m4gshm.storage.ReadStorage;
import payments.data.model.Account;
import reactor.core.publisher.Mono;

public interface AccountStorage extends ReadStorage<Account, String> {
    Mono<Boolean> addLock(Account account, Double amount, String txid, boolean twoPhaseCommit);
}
