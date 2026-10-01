package gatling.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.ArrayList;
import java.util.List;

public final class JsonUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private JsonUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String toJson(Object object) {
        try {
            return MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize object to JSON", e);
        }
    }

    public static JsonNode readTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }

    public static String text(JsonNode record, String field) {
        JsonNode node = record.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return "";
        }
        return node.asText("").trim();
    }

    public static String firstNonBlankText(JsonNode record, String... fields) {
        for (String field : fields) {
            String value = text(record, field);
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    public static List<String> extractArrayElements(String json, String... wrapperKeys) {
        List<String> elements = new ArrayList<>();
        try {
            JsonNode array = locateArray(readTree(json), wrapperKeys);
            if (array != null) {
                for (JsonNode element : array) {
                    elements.add(toJson(element));
                }
            }
        } catch (RuntimeException ignored) {
            // invalid JSON — return an empty list
        }
        return elements;
    }

    private static JsonNode locateArray(JsonNode root, String... wrapperKeys) {
        if (root.isArray()) {
            return root;
        }
        for (String key : wrapperKeys) {
            JsonNode candidate = root.path(key);
            if (candidate.isArray()) {
                return candidate;
            }
        }
        return null;
    }
}
