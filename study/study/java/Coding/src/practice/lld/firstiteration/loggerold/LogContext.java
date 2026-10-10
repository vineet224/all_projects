package practice.lld.firstiteration.loggerold;

import java.util.HashMap;
import java.util.Map;

public final class LogContext {
    private static final ThreadLocal<Map<String, String>> CONTEXT =
            ThreadLocal.withInitial(HashMap::new);

    private LogContext() {}

    public static void put(String key, String value) {
        if (key == null) return;
        if (value == null) {
            CONTEXT.get().remove(key);
        } else {
            CONTEXT.get().put(key, value);
        }
    }

    public static String get(String key) {
        if (key == null) return null;
        return CONTEXT.get().get(key);
    }

    public static void clear() {
        CONTEXT.get().clear();
    }
}

