package in.craves.integration.delivery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DeliveryJsonSupport {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Boolean>> BOOLEAN_MAP = new TypeReference<>() {};
    private static final TypeReference<Map<String, Double>> DOUBLE_MAP = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public DeliveryJsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize delivery data", ex);
        }
    }

    public String writeNode(JsonNode value) {
        return value == null ? "{}" : value.toString();
    }

    public JsonNode readTree(String value) {
        try {
            return value == null ? objectMapper.createObjectNode() : objectMapper.readTree(value);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    public List<String> readStringList(String value) {
        try {
            return value == null ? List.of() : objectMapper.readValue(value, STRING_LIST);
        } catch (Exception ex) {
            return List.of();
        }
    }

    public Map<String, Boolean> readBooleanMap(String value) {
        try {
            return value == null ? Map.of() : objectMapper.readValue(value, BOOLEAN_MAP);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    public Map<String, Double> readDoubleMap(String value) {
        try {
            return value == null ? Map.of() : objectMapper.readValue(value, DOUBLE_MAP);
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
