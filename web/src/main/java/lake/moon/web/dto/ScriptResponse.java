package lake.moon.web.dto;

public record ScriptResponse(int code, Object result, String error, long elapsedMs) {

    public static ScriptResponse success(Object result, long elapsedMs) {
        return new ScriptResponse(200, result, null, elapsedMs);
    }

    public static ScriptResponse error(int code, String error, long elapsedMs) {
        return new ScriptResponse(code, null, error, elapsedMs);
    }
}
