package jatot.json;

public class JsonMappingException extends JsonException {
    public JsonMappingException(String message) {
        super(message);
    }
    public JsonMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
