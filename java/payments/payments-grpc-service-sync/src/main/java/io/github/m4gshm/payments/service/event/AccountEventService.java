package io.github.m4gshm.payments.service.event;

import java.time.OffsetDateTime;

public interface AccountEventService {
    void sendAccountBalanceEvent(String clientId, double balance, OffsetDateTime timestamp);
}
