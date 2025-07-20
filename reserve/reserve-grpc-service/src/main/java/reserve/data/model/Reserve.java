package reserve.data.model;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

@Builder
public record Reserve(String id,
                      String externalRef,
                      Status status,
                      OffsetDateTime createdAt,
                      OffsetDateTime updatedAt,
                      List<Item> items) {
    public enum Status {
        CREATED,
        VALIDATED,
        CANCELLED;

        private final static Map<String, Status> byCode = Arrays.stream(Status.values()).collect(toMap(Status::getCode, status -> status));

        public static Status byCode(String code) {
            return byCode.get(code);
        }

        public String getCode() {
            return name();
        }
    }

    @Builder
    public record Item(String id, Integer count) {

    }
}
