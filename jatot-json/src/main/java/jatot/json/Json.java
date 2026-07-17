package jatot.json;

import jatot.json.internal.JsonMapper;
import jatot.json.internal.JsonParser;
import jatot.json.internal.JsonStringifier;

import java.util.List;

public class Json {
    private final JsonOptions options;
    private final JsonMapper mapper;
    private final JsonStringifier stringifier;

    public Json() {
        this(JsonOptions.DEFAULT);
    }

    public Json(JsonOptions options) {
        this.options = options;
        this.mapper = new JsonMapper(options);
        this.stringifier = new JsonStringifier(options);
    }

    public static <T extends Record> T parse(String json, Class<T> clazz) {
        return new Json().parseInternal(json, clazz);
    }

    public static <T extends Record> List<T> parseList(String json, Class<T> clazz) {
        return new Json().parseListInternal(json, clazz);
    }

    public static String stringify(Object obj) {
        return new Json().stringifyInternal(obj);
    }

    public static boolean isValid(String json) {
        try {
            JsonParser.parse(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public <T extends Record> T parseInternal(String json, Class<T> clazz) {
        if (!clazz.isRecord()) {
            throw new JsonMappingException("Only Java Records are supported for parsing.");
        }
        Object parsed = JsonParser.parse(json);
        return mapper.mapToRecord(parsed, clazz);
    }

    public <T extends Record> List<T> parseListInternal(String json, Class<T> clazz) {
        if (!clazz.isRecord()) {
            throw new JsonMappingException("Only Java Records are supported for parsing.");
        }
        Object parsed = JsonParser.parse(json);
        if (!(parsed instanceof List)) {
            throw new JsonMappingException("Expected JSON array");
        }
        return mapper.mapToList((List<?>) parsed, clazz);
    }

    public String stringifyInternal(Object obj) {
        return stringifier.stringify(obj);
    }
}
