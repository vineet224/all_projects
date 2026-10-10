package practice.lld.firstiteration.loggerold;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class Logger {
    private final Map<LogLevel, List<PrinterInterface>> printersByLevel;

    private final LogFormatter logFormatter;

    public Logger(){
        this(
                new LogFormatter("traceId: {{X.traceId}}, userId: {{X.userId}} "),
                defaultPrinters()
        );
    }

    public Logger(LogFormatter logFormatter, Map<LogLevel, List<PrinterInterface>> printersByLevel) {
        this.logFormatter = logFormatter;
        this.printersByLevel = printersByLevel;
    }

    private static Map<LogLevel, List<PrinterInterface>> defaultPrinters() {
        TerminalPrinter terminalPrinter = new TerminalPrinter();
        Map<LogLevel, List<PrinterInterface>> map = new EnumMap<>(LogLevel.class);
        for (LogLevel level : LogLevel.values()) {
            map.put(level, new ArrayList<>());
        }
        map.get(LogLevel.INFO).add(terminalPrinter);
        map.get(LogLevel.DEBUG).add(terminalPrinter);
        map.get(LogLevel.ERROR).add(terminalPrinter);
        return map;
    }

    public void info(String messageToPrint){
        log(LogLevel.INFO, messageToPrint);
    }

    public void debug(String messageToPrint){
        log(LogLevel.DEBUG, messageToPrint);
    }

    public void error(String messageToPrint){
        log(LogLevel.ERROR, messageToPrint);
    }

    private void log(LogLevel level, String messageToPrint) {
        String formatted = logFormatter.formatString(messageToPrint);
        LogMessage logMessage = new LogMessage(level, formatted);
        List<PrinterInterface> printers = printersByLevel.get(level);
        if (printers == null) return;

        for (PrinterInterface printer : printers) {
            printer.print(logMessage);
        }
    }

}
