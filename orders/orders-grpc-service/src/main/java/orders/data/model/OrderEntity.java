package orders.data.model;

import lombok.Builder;

import java.util.UUID;

@Builder
public record OrderEntity(UUID id) {
}
