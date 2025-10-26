package io.github.m4gshm.payments.event.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AccountBalanceEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    public void testJsonSerialize() throws JsonProcessingException {
        var clientId = "42488ab7-822b-4838-a338-e9d93adf91de";
        var timestamp = OffsetDateTime.of(LocalDateTime.of(2025, 10, 4, 9, 0), UTC);
        var json = objectMapper.writeValueAsString(AccountBalanceEvent.builder()
                .balance(100)
                .clientId(clientId)
                .timestamp(timestamp)
                .build());

        assertEquals(
                """
                        {"requestId":null,"clientId":"42488ab7-822b-4838-a338-e9d93adf91de","balance":100.0,"timestamp":"2025-10-04T09:00:00Z"}
                        """
                        .trim(),
                json);
    }
}
