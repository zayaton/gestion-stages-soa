package ma.inpt.stages.common.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.ws.rs.ext.ContextResolver;

/**
 * Réglages JSON communs : dates ISO 8601, champs nuls omis, champ inconnu refusé.
 * Refuser un champ inconnu évite qu'une faute de frappe (« status » au lieu de « statut ») passe inaperçue.
 */
public final class Json implements ContextResolver<ObjectMapper> {

    public static final ObjectMapper MAPPEUR = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Override
    public ObjectMapper getContext(Class<?> type) {
        return MAPPEUR;
    }
}
