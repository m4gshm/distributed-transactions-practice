package payments.data.model;

import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record Account(String clientId, double amount, double locked, OffsetDateTime updatedAt) {
}
