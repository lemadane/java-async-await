package jatot.json;

public class JsonParseException extends JsonException {
    public JsonParseException(String message) {
        super(message);
    }
    public JsonParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
