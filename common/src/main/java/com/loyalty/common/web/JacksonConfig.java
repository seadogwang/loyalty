package com.loyalty.common.web;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Global JSON conventions (design 3.3 / 24.10):
 * <ul>
 *   <li>amounts / points as decimal <strong>strings</strong> to avoid float drift;</li>
 *   <li>times as ISO-8601 UTC instants (jsr310).</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer loyaltyJsonCustomizer() {
        return builder -> {
            SimpleModule decimalModule = new SimpleModule("loyalty-decimals");
            decimalModule.addSerializer(BigDecimal.class, new BigDecimalAsStringSerializer());
            decimalModule.addDeserializer(BigDecimal.class, new BigDecimalFromStringDeserializer());
            builder.modulesToInstall(decimalModule, new JavaTimeModule());
            builder.featuresToDisable(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS);
        };
    }

    static final class BigDecimalAsStringSerializer extends JsonSerializer<BigDecimal> {
        @Override
        public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            // Normalized scale to 2 for monetary / point amounts, emitted as a JSON string.
            gen.writeString(value.setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString());
        }
    }

    static final class BigDecimalFromStringDeserializer extends JsonDeserializer<BigDecimal> {
        @Override
        public BigDecimal deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String text = p.getValueAsString();
            if (text == null) {
                // fall back to numeric token if a number was supplied
                return p.getDecimalValue();
            }
            return new BigDecimal(text.trim());
        }
    }
}
