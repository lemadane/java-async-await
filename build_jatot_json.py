import os

base_dir = '/home/lem/Projects/java/jatot/jatot-json'
main_src = os.path.join(base_dir, 'src/main/java/jatot/json')
test_src = os.path.join(base_dir, 'src/test/java/jatot/json')

os.makedirs(main_src, exist_ok=True)
os.makedirs(test_src, exist_ok=True)
os.makedirs(os.path.join(main_src, 'internal'), exist_ok=True)

def write_file(path, content):
    with open(path, 'w') as f:
        f.write(content.strip() + '\n')

write_file(os.path.join(main_src, 'JsonException.java'), """
package jatot.json;

public class JsonException extends RuntimeException {
    public JsonException(String message) {
        super(message);
    }

    public JsonException(String message, Throwable cause) {
        super(message, cause);
    }
}
""")

write_file(os.path.join(main_src, 'JsonParseException.java'), """
package jatot.json;

public class JsonParseException extends JsonException {
    public JsonParseException(String message) {
        super(message);
    }
    public JsonParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
""")

write_file(os.path.join(main_src, 'JsonMappingException.java'), """
package jatot.json;

public class JsonMappingException extends JsonException {
    public JsonMappingException(String message) {
        super(message);
    }
    public JsonMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
""")

write_file(os.path.join(main_src, 'JsonName.java'), """
package jatot.json;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonName {
    String value();
}
""")

write_file(os.path.join(main_src, 'JsonIgnore.java'), """
package jatot.json;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonIgnore {
}
""")

write_file(os.path.join(main_src, 'JsonNamingPolicy.java'), """
package jatot.json;

public enum JsonNamingPolicy {
    IDENTITY,
    CAMEL_CASE,
    SNAKE_CASE,
    KEBAB_CASE,
    PASCAL_CASE;

    public String translateName(String input) {
        if (input == null || input.isEmpty()) return input;
        switch (this) {
            case IDENTITY: return input;
            case CAMEL_CASE: return toCamel(input);
            case SNAKE_CASE: return toSnake(input);
            case KEBAB_CASE: return toKebab(input);
            case PASCAL_CASE: return toPascal(input);
            default: return input;
        }
    }

    private String toCamel(String s) {
        if (s.isEmpty()) return s;
        if (Character.isUpperCase(s.charAt(0))) {
            return Character.toLowerCase(s.charAt(0)) + s.substring(1);
        }
        return s;
    }

    private String toPascal(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String toSnake(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String toKebab(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('-');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
""")

write_file(os.path.join(main_src, 'JsonOptions.java'), """
package jatot.json;

public record JsonOptions(
    JsonNamingPolicy namingPolicy,
    boolean ignoreUnknownProperties,
    boolean prettyPrint
) {
    public static final JsonOptions DEFAULT = new JsonOptions(JsonNamingPolicy.IDENTITY, true, false);
}
""")

write_file(os.path.join(main_src, 'Json.java'), """
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
""")

write_file(os.path.join(main_src, 'internal', 'JsonParser.java'), """
package jatot.json.internal;

import jatot.json.JsonParseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JsonParser {
    private final String json;
    private int pos = 0;

    private JsonParser(String json) {
        this.json = json;
    }

    public static Object parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new JsonParseException("Empty JSON string");
        }
        JsonParser parser = new JsonParser(json);
        Object result = parser.parseValue();
        parser.skipWhitespace();
        if (parser.pos < parser.json.length()) {
            throw new JsonParseException("Unexpected extra data after JSON value at pos " + parser.pos);
        }
        return result;
    }

    private Object parseValue() {
        skipWhitespace();
        if (pos >= json.length()) throw new JsonParseException("Unexpected end of JSON");

        char c = json.charAt(pos);
        if (c == '{') return parseObject();
        if (c == '[') return parseArray();
        if (c == '"') return parseString();
        if (c == 't') return parseLiteral("true", true);
        if (c == 'f') return parseLiteral("false", false);
        if (c == 'n') return parseLiteral("null", null);
        if (c == '-' || (c >= '0' && c <= '9')) return parseNumber();

        throw new JsonParseException("Unexpected character '" + c + "' at pos " + pos);
    }

    private Map<String, Object> parseObject() {
        pos++; // skip '{'
        Map<String, Object> map = new LinkedHashMap<>();
        skipWhitespace();
        if (pos < json.length() && json.charAt(pos) == '}') {
            pos++;
            return map;
        }

        while (pos < json.length()) {
            skipWhitespace();
            if (json.charAt(pos) != '"') throw new JsonParseException("Expected string key in object at pos " + pos);
            String key = parseString();

            skipWhitespace();
            if (pos >= json.length() || json.charAt(pos) != ':') throw new JsonParseException("Expected ':' in object at pos " + pos);
            pos++; // skip ':'

            Object value = parseValue();
            map.put(key, value);

            skipWhitespace();
            if (pos >= json.length()) throw new JsonParseException("Unterminated object");
            char c = json.charAt(pos);
            if (c == '}') {
                pos++;
                return map;
            } else if (c == ',') {
                pos++;
            } else {
                throw new JsonParseException("Expected ',' or '}' in object at pos " + pos);
            }
        }
        throw new JsonParseException("Unterminated object");
    }

    private List<Object> parseArray() {
        pos++; // skip '['
        List<Object> list = new ArrayList<>();
        skipWhitespace();
        if (pos < json.length() && json.charAt(pos) == ']') {
            pos++;
            return list;
        }

        while (pos < json.length()) {
            list.add(parseValue());

            skipWhitespace();
            if (pos >= json.length()) throw new JsonParseException("Unterminated array");
            char c = json.charAt(pos);
            if (c == ']') {
                pos++;
                return list;
            } else if (c == ',') {
                pos++;
            } else {
                throw new JsonParseException("Expected ',' or ']' in array at pos " + pos);
            }
        }
        throw new JsonParseException("Unterminated array");
    }

    private String parseString() {
        pos++; // skip '"'
        StringBuilder sb = new StringBuilder();
        while (pos < json.length()) {
            char c = json.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            } else if (c == '\\\\') {
                if (pos >= json.length()) throw new JsonParseException("Unterminated escape sequence");
                char e = json.charAt(pos++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\\\': sb.append('\\\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\\b'); break;
                    case 'f': sb.append('\\f'); break;
                    case 'n': sb.append('\\n'); break;
                    case 'r': sb.append('\\r'); break;
                    case 't': sb.append('\\t'); break;
                    case 'u':
                        if (pos + 4 > json.length()) throw new JsonParseException("Unterminated unicode escape");
                        String hex = json.substring(pos, pos + 4);
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos += 4;
                        break;
                    default: throw new JsonParseException("Invalid escape sequence \\\\" + e);
                }
            } else {
                sb.append(c);
            }
        }
        throw new JsonParseException("Unterminated string");
    }

    private Number parseNumber() {
        int start = pos;
        while (pos < json.length()) {
            char c = json.charAt(pos);
            if (c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E' || (c >= '0' && c <= '9')) {
                pos++;
            } else {
                break;
            }
        }
        String numStr = json.substring(start, pos);
        try {
            if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E")) {
                return Double.parseDouble(numStr);
            } else {
                long l = Long.parseLong(numStr);
                if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                    return (int) l;
                }
                return l;
            }
        } catch (NumberFormatException e) {
            throw new JsonParseException("Invalid number format: " + numStr);
        }
    }

    private Object parseLiteral(String literal, Object value) {
        if (pos + literal.length() <= json.length() && json.substring(pos, pos + literal.length()).equals(literal)) {
            pos += literal.length();
            return value;
        }
        throw new JsonParseException("Expected literal '" + literal + "' at pos " + pos);
    }

    private void skipWhitespace() {
        while (pos < json.length()) {
            char c = json.charAt(pos);
            if (c == ' ' || c == '\\t' || c == '\\r' || c == '\\n') {
                pos++;
            } else {
                break;
            }
        }
    }
}
""")

write_file(os.path.join(main_src, 'internal', 'JsonMapper.java'), """
package jatot.json.internal;

import jatot.json.JsonIgnore;
import jatot.json.JsonMappingException;
import jatot.json.JsonName;
import jatot.json.JsonOptions;

import java.lang.reflect.*;
import java.time.*;
import java.util.*;

public class JsonMapper {
    private final JsonOptions options;

    public JsonMapper(JsonOptions options) {
        this.options = options;
    }

    @SuppressWarnings("unchecked")
    public <T extends Record> T mapToRecord(Object jsonObj, Class<T> clazz) {
        if (jsonObj == null) return null;
        if (!(jsonObj instanceof Map)) {
            throw new JsonMappingException("Expected JSON object to map to " + clazz.getName());
        }
        Map<String, Object> map = (Map<String, Object>) jsonObj;

        RecordComponent[] components = clazz.getRecordComponents();
        Object[] args = new Object[components.length];
        Class<?>[] paramTypes = new Class<?>[components.length];

        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            paramTypes[i] = component.getType();
            if (component.isAnnotationPresent(JsonIgnore.class)) {
                args[i] = defaultValue(paramTypes[i]);
                continue;
            }

            String jsonKey = component.getName();
            if (component.isAnnotationPresent(JsonName.class)) {
                jsonKey = component.getAnnotation(JsonName.class).value();
            } else {
                jsonKey = options.namingPolicy().translateName(jsonKey);
            }

            if (map.containsKey(jsonKey)) {
                args[i] = mapValue(map.get(jsonKey), paramTypes[i], component.getGenericType());
            } else {
                args[i] = defaultValue(paramTypes[i]);
            }
        }

        if (!options.ignoreUnknownProperties()) {
            Set<String> knownKeys = new HashSet<>();
            for (RecordComponent component : components) {
                if (component.isAnnotationPresent(JsonIgnore.class)) continue;
                String k = component.isAnnotationPresent(JsonName.class) 
                    ? component.getAnnotation(JsonName.class).value() 
                    : options.namingPolicy().translateName(component.getName());
                knownKeys.add(k);
            }
            for (String key : map.keySet()) {
                if (!knownKeys.contains(key)) {
                    throw new JsonMappingException("Unknown property: " + key + " for record " + clazz.getName());
                }
            }
        }

        try {
            Constructor<T> ctor = clazz.getDeclaredConstructor(paramTypes);
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        } catch (Exception e) {
            throw new JsonMappingException("Failed to instantiate record " + clazz.getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends Record> List<T> mapToList(List<?> list, Class<T> clazz) {
        List<T> result = new ArrayList<>();
        for (Object item : list) {
            result.add(mapToRecord(item, clazz));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object mapValue(Object val, Class<?> type, Type genericType) {
        if (val == null) return null;

        if (type == Optional.class) {
            if (genericType instanceof ParameterizedType pt) {
                Type argType = pt.getActualTypeArguments()[0];
                return Optional.ofNullable(mapValue(val, (Class<?>) getRawType(argType), argType));
            }
            return Optional.of(val);
        }

        if (type.isRecord()) {
            return mapToRecord(val, (Class<? extends Record>) type);
        }

        if (type == String.class) {
            return String.valueOf(val);
        }

        if (type == UUID.class) {
            return UUID.fromString(String.valueOf(val));
        }

        if (type == LocalDate.class) return LocalDate.parse(String.valueOf(val));
        if (type == LocalTime.class) return LocalTime.parse(String.valueOf(val));
        if (type == LocalDateTime.class) return LocalDateTime.parse(String.valueOf(val));
        if (type == ZonedDateTime.class) return ZonedDateTime.parse(String.valueOf(val));
        if (type == OffsetDateTime.class) return OffsetDateTime.parse(String.valueOf(val));
        if (type == Instant.class) return Instant.parse(String.valueOf(val));

        if (type.isEnum()) {
            String s = String.valueOf(val);
            for (Object enumConst : type.getEnumConstants()) {
                if (((Enum<?>) enumConst).name().equals(s)) {
                    return enumConst;
                }
            }
            throw new JsonMappingException("Unknown enum value: " + s + " for " + type.getName());
        }

        if (type == boolean.class || type == Boolean.class) {
            if (val instanceof Boolean b) return b;
            return Boolean.parseBoolean(String.valueOf(val));
        }

        if (type == int.class || type == Integer.class) {
            if (val instanceof Number n) return n.intValue();
            return Integer.parseInt(String.valueOf(val));
        }

        if (type == long.class || type == Long.class) {
            if (val instanceof Number n) return n.longValue();
            return Long.parseLong(String.valueOf(val));
        }

        if (type == double.class || type == Double.class) {
            if (val instanceof Number n) return n.doubleValue();
            return Double.parseDouble(String.valueOf(val));
        }

        if (type == float.class || type == Float.class) {
            if (val instanceof Number n) return n.floatValue();
            return Float.parseFloat(String.valueOf(val));
        }

        if (List.class.isAssignableFrom(type) || Set.class.isAssignableFrom(type)) {
            if (val instanceof List<?> l) {
                Collection<Object> c = Set.class.isAssignableFrom(type) ? new HashSet<>() : new ArrayList<>();
                Type elementType = Object.class;
                if (genericType instanceof ParameterizedType pt) {
                    elementType = pt.getActualTypeArguments()[0];
                }
                for (Object item : l) {
                    c.add(mapValue(item, (Class<?>) getRawType(elementType), elementType));
                }
                return c;
            }
            throw new JsonMappingException("Expected JSON array for collection");
        }

        if (Map.class.isAssignableFrom(type)) {
            if (val instanceof Map<?, ?> m) {
                Map<String, Object> res = new HashMap<>();
                Type valueType = Object.class;
                if (genericType instanceof ParameterizedType pt) {
                    valueType = pt.getActualTypeArguments()[1];
                }
                for (Map.Entry<?, ?> entry : m.entrySet()) {
                    res.put(String.valueOf(entry.getKey()), mapValue(entry.getValue(), (Class<?>) getRawType(valueType), valueType));
                }
                return res;
            }
            throw new JsonMappingException("Expected JSON object for map");
        }

        return val;
    }

    private Class<?> getRawType(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType pt) {
            return (Class<?>) pt.getRawType();
        }
        return Object.class;
    }

    private Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class || type == short.class || type == byte.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class || type == float.class) return 0.0;
        if (type == Optional.class) return Optional.empty();
        return null;
    }
}
""")

write_file(os.path.join(main_src, 'internal', 'JsonStringifier.java'), """
package jatot.json.internal;

import jatot.json.JsonIgnore;
import jatot.json.JsonName;
import jatot.json.JsonOptions;

import java.lang.reflect.RecordComponent;
import java.time.temporal.TemporalAccessor;
import java.util.*;

public class JsonStringifier {
    private final JsonOptions options;

    public JsonStringifier(JsonOptions options) {
        this.options = options;
    }

    public String stringify(Object obj) {
        StringBuilder sb = new StringBuilder();
        stringifyInternal(obj, sb, 0);
        return sb.toString();
    }

    private void stringifyInternal(Object obj, StringBuilder sb, int depth) {
        if (obj == null) {
            sb.append("null");
            return;
        }

        if (obj instanceof Optional<?> opt) {
            stringifyInternal(opt.orElse(null), sb, depth);
            return;
        }

        if (obj instanceof String || obj instanceof UUID || obj instanceof Enum || obj instanceof TemporalAccessor) {
            sb.append('"');
            escapeString(obj.toString(), sb);
            sb.append('"');
            return;
        }

        if (obj instanceof Number || obj instanceof Boolean) {
            sb.append(obj.toString());
            return;
        }

        if (obj instanceof Collection<?> coll) {
            sb.append('[');
            boolean first = true;
            for (Object item : coll) {
                if (!first) sb.append(',');
                first = false;
                if (options.prettyPrint()) {
                    sb.append("\\n");
                    indent(sb, depth + 1);
                }
                stringifyInternal(item, sb, depth + 1);
            }
            if (options.prettyPrint() && !coll.isEmpty()) {
                sb.append("\\n");
                indent(sb, depth);
            }
            sb.append(']');
            return;
        }

        if (obj instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                if (options.prettyPrint()) {
                    sb.append("\\n");
                    indent(sb, depth + 1);
                }
                sb.append('"');
                escapeString(String.valueOf(entry.getKey()), sb);
                sb.append("\":");
                if (options.prettyPrint()) sb.append(" ");
                stringifyInternal(entry.getValue(), sb, depth + 1);
            }
            if (options.prettyPrint() && !map.isEmpty()) {
                sb.append("\\n");
                indent(sb, depth);
            }
            sb.append('}');
            return;
        }

        if (obj.getClass().isRecord()) {
            sb.append('{');
            boolean first = true;
            try {
                for (RecordComponent comp : obj.getClass().getRecordComponents()) {
                    if (comp.isAnnotationPresent(JsonIgnore.class)) continue;
                    
                    Object val = comp.getAccessor().invoke(obj);
                    if (val == null) continue; // Skip nulls or keep? Usually skip or explicit. Let's keep for simplicity.
                    
                    if (!first) sb.append(',');
                    first = false;
                    
                    if (options.prettyPrint()) {
                        sb.append("\\n");
                        indent(sb, depth + 1);
                    }
                    
                    String key = comp.getName();
                    if (comp.isAnnotationPresent(JsonName.class)) {
                        key = comp.getAnnotation(JsonName.class).value();
                    } else {
                        key = options.namingPolicy().translateName(key);
                    }
                    
                    sb.append('"');
                    escapeString(key, sb);
                    sb.append("\":");
                    if (options.prettyPrint()) sb.append(" ");
                    stringifyInternal(val, sb, depth + 1);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to stringify record", e);
            }
            if (options.prettyPrint() && !first) {
                sb.append("\\n");
                indent(sb, depth);
            }
            sb.append('}');
            return;
        }

        throw new IllegalArgumentException("Unsupported type for stringification: " + obj.getClass());
    }

    private void escapeString(String s, StringBuilder sb) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\\\\""); break;
                case '\\\\': sb.append("\\\\\\\\"); break;
                case '\\b': sb.append("\\\\b"); break;
                case '\\f': sb.append("\\\\f"); break;
                case '\\n': sb.append("\\\\n"); break;
                case '\\r': sb.append("\\\\r"); break;
                case '\\t': sb.append("\\\\t"); break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
    }

    private void indent(StringBuilder sb, int depth) {
        for (int i = 0; i < depth * 2; i++) {
            sb.append(' ');
        }
    }
}
""")

write_file(os.path.join(test_src, 'JsonTest.java'), """
package jatot.json;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class JsonTest {

    public enum Status { ACTIVE, INACTIVE }

    public record User(
        @JsonName("user_id") UUID id,
        String name,
        int age,
        Status status,
        List<String> tags,
        Map<String, Integer> scores,
        @JsonIgnore String secret,
        Optional<String> bio,
        Address address,
        Instant createdAt
    ) {}

    public record Address(String city, String zip) {}

    @Test
    void testValid() {
        assertTrue(Json.isValid("{\\"a\\": 1}"));
        assertFalse(Json.isValid("{\\"a\\": 1"));
    }

    @Test
    void testParseAndStringify() {
        String json = \"\"\"
        {
            "user_id": "123e4567-e89b-12d3-a456-426614174000",
            "name": "Alice",
            "age": 30,
            "status": "ACTIVE",
            "tags": ["admin", "user"],
            "scores": {"math": 100, "science": 90},
            "secret": "should_be_ignored",
            "bio": "Hello World",
            "address": {"city": "Wonderland", "zip": "12345"},
            "createdAt": "2023-10-01T12:00:00Z"
        }
        \"\"\";

        User user = Json.parse(json, User.class);
        assertEquals(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), user.id());
        assertEquals("Alice", user.name());
        assertEquals(30, user.age());
        assertEquals(Status.ACTIVE, user.status());
        assertEquals(List.of("admin", "user"), user.tags());
        assertEquals(Map.of("math", 100, "science", 90), user.scores());
        assertNull(user.secret());
        assertEquals(Optional.of("Hello World"), user.bio());
        assertEquals("Wonderland", user.address().city());
        assertEquals(Instant.parse("2023-10-01T12:00:00Z"), user.createdAt());

        String stringified = Json.stringify(user);
        assertTrue(stringified.contains("\\"user_id\\":\\"123e4567-e89b-12d3-a456-426614174000\\""));
        assertTrue(stringified.contains("\\"name\\":\\"Alice\\""));
        assertFalse(stringified.contains("secret"));
    }

    @Test
    void testRejectClass() {
        class NotARecord {
            public String name;
        }
        assertThrows(JsonMappingException.class, () -> Json.parse("{}", NotARecord.class));
    }

    public record SnakeTest(String myName, int myAge) {}

    @Test
    void testNamingPolicy() {
        Json json = new Json(new JsonOptions(JsonNamingPolicy.SNAKE_CASE, true, false));
        SnakeTest obj = json.parseInternal("{\\"my_name\\":\\"Bob\\", \\"my_age\\":25}", SnakeTest.class);
        assertEquals("Bob", obj.myName());
        assertEquals(25, obj.myAge());
        
        String s = json.stringifyInternal(obj);
        assertTrue(s.contains("\\"my_name\\":\\"Bob\\""));
        assertTrue(s.contains("\\"my_age\\":25"));
    }
}
""")
