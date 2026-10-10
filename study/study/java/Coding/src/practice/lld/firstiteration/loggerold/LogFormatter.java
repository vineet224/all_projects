package practice.lld.firstiteration.loggerold;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogFormatter {

    // this class takes the message and the configuration formatting style

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{\\{X\\.(\\w+)\\}\\}");

    private final String styleConfig;
    private final ArrayList<String> fieldsToInject;

    public LogFormatter(String styleConfig){
        this.styleConfig = styleConfig;
        this.fieldsToInject = new ArrayList<>();

        Matcher matcher = TOKEN_PATTERN.matcher(styleConfig);
        while (matcher.find()) {
            fieldsToInject.add(matcher.group(1));
        }
    }

    public String formatString(String message){
        String prefix = styleConfig;
        for (String key : fieldsToInject) {
            String value = LogContext.get(key);
            prefix = prefix.replace("{{X." + key + "}}", value == null ? "null" : value);
        }
        return prefix + message;
    }
}
