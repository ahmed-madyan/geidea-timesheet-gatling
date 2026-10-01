package gatling.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Utility for reading JSON files from the classpath and converting them into Java objects.
 */
public final class JsonDataReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonDataReader() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Reads a JSON file from the classpath and returns the corresponding Java object.
     *
     * @param resourcePath the path to the JSON file on the classpath
     * @param clazz        the target type
     * @param <T>          the generic type parameter
     * @return an instance of {@code T}
     * @throws RuntimeException if the resource cannot be found or parsing fails
     */
    public static <T> T read(String resourcePath, Class<T> clazz) {
        try (InputStream is = getResourceAsStream(resourcePath)) {
            return MAPPER.readValue(is, clazz);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read JSON file from resource: " + resourcePath, e);
        }
    }

    /**
     * Reads a JSON array from the classpath and returns a list of the corresponding Java objects.
     *
     * @param resourcePath the path to the JSON file on the classpath
     * @param clazz        the type of elements in the list
     * @param <T>          the generic type parameter
     * @return a List of {@code T}
     * @throws RuntimeException if the resource cannot be found or parsing fails
     */
    public static <T> List<T> readList(String resourcePath, Class<T> clazz) {
        try (InputStream is = getResourceAsStream(resourcePath)) {
            return MAPPER.readValue(is, MAPPER.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read JSON list from resource: " + resourcePath, e);
        }
    }

    /**
     * Reads a JSON array from the classpath using a Jackson {@link TypeReference}.
     * Useful for complex generic types.
     *
     * @param resourcePath the path to the JSON file on the classpath
     * @param typeRef      the Jackson type reference
     * @param <T>          the generic type parameter
     * @return an instance of {@code T}
     * @throws RuntimeException if the resource cannot be found or parsing fails
     */
    public static <T> T readWithTypeRef(String resourcePath, TypeReference<T> typeRef) {
        try (InputStream is = getResourceAsStream(resourcePath)) {
            return MAPPER.readValue(is, typeRef);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read JSON from resource with TypeRef: " + resourcePath, e);
        }
    }


    /**
     * Reads a JSON file from the classpath and returns the JsonNode at the specified JSON Pointer path.
     * The JSON path should be a valid Jackson JSON Pointer expression (e.g., "/user/name", "/arr/0/id").
     *
     * @param resourcePath the path to the JSON file on the classpath
     * @param jsonPointer  the JSON pointer expression starting with '/'
     * @return the {@link JsonNode} representing the desired JSON object or value, or a "missing node" if not found
     * @throws RuntimeException if the resource cannot be found or parsing fails
     */
    public static JsonNode readNode(String resourcePath, String jsonPointer) {
        try (InputStream is = getResourceAsStream(resourcePath)) {
            JsonNode rootNode = MAPPER.readTree(is);
            return rootNode.at("/" + jsonPointer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read JSON node from resource: " + resourcePath, e);
        }
    }

    /**
     * Reads a JSON file from the classpath, extracts the JSON object at the specified JSON Pointer path,
     * and maps it to the given Java class type.
     *
     * @param resourcePath the path to the JSON file on the classpath
     * @param jsonPointer  the JSON pointer expression starting with '/' (e.g., "/user/details")
     * @param clazz        the target type class
     * @param <T>          the generic type parameter
     * @return an instance of {@code T}, or null if the node cannot be found
     * @throws RuntimeException if the resource cannot be found or parsing fails
     */
    public static <T> T readObjectAtPath(String resourcePath, String jsonPointer, Class<T> clazz) {
        try (InputStream is = getResourceAsStream(resourcePath)) {
            JsonNode rootNode = MAPPER.readTree(is);
            JsonNode targetNode = rootNode.at(jsonPointer);
            if (targetNode.isMissingNode()) {
                return null;
            }
            return MAPPER.treeToValue(targetNode, clazz);
        } catch (IllegalArgumentException | IOException e) {
            throw new RuntimeException("Failed to read JSON object at path '" + jsonPointer + "' from resource: " + resourcePath, e);
        }
    }

    private static InputStream getResourceAsStream(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            throw new IllegalArgumentException("Resource path must not be null or blank");
        }
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
        if (is == null) {
            throw new IllegalArgumentException("Resource not found on classpath: " + resourcePath);
        }
        return is;
    }
}
