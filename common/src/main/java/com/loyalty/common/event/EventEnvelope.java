package com.loyalty.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.UUID;

/**
 * CloudEvents-style envelope (design 20.1 / 20.2). All cross-service events are
 * published in this shape; the {@code data} payload is domain-specific and versioned
 * via the {@code type} suffix (e.g. {@code loyalty.point.earned.v1}).
 *
 * <p>Authorization events must never embed JWT, password, or raw identity values in
 * {@code data}; this is enforced at the producer site.
 */
@JsonPropertyOrder({
        "specversion", "id", "type", "source", "subject", "time",
        "datacontenttype", "tenantid", "programid", "correlationid", "causationid", "data"
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventEnvelope(
        @JsonProperty("specversion") String specVersion,
        @JsonProperty("id") UUID id,
        @JsonProperty("type") String type,
        @JsonProperty("source") String source,
        @JsonProperty("subject") String subject,
        @JsonProperty("time") Instant time,
        @JsonProperty("datacontenttype") String dataContentType,
        @JsonProperty("tenantid") UUID tenantId,
        @JsonProperty("programid") UUID programId,
        @JsonProperty("correlationid") String correlationId,
        @JsonProperty("causationid") String causationId,
        @JsonProperty("data") Object data) {

    public static final String SPEC_VERSION = "1.0";
    public static final String CONTENT_TYPE = "application/json";

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID id = UUID.randomUUID();
        private String type;
        private String source;
        private String subject;
        private Instant time = Instant.now();
        private UUID tenantId;
        private UUID programId;
        private String correlationId;
        private String causationId;
        private Object data;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder type(String type) { this.type = type; return this; }
        public Builder source(String source) { this.source = source; return this; }
        public Builder subject(String subject) { this.subject = subject; return this; }
        public Builder time(Instant time) { this.time = time; return this; }
        public Builder tenantId(UUID tenantId) { this.tenantId = tenantId; return this; }
        public Builder programId(UUID programId) { this.programId = programId; return this; }
        public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public Builder causationId(String causationId) { this.causationId = causationId; return this; }
        public Builder data(Object data) { this.data = data; return this; }

        public EventEnvelope build() {
            if (type == null || source == null || subject == null || data == null) {
                throw new IllegalStateException("type, source, subject and data are required");
            }
            return new EventEnvelope(SPEC_VERSION, id, type, source, subject, time,
                    CONTENT_TYPE, tenantId, programId, correlationId, causationId, data);
        }
    }
}
