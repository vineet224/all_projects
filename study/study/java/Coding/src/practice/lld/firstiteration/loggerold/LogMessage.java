package practice.lld.firstiteration.loggerold;

import java.util.Date;

public class LogMessage {
    private final Date timestamp;
    private final LogLevel logLevel;
    private final String message;


    public String toString(){
        return " " + timestamp + " " + logLevel + " " + message;
    }

    public LogMessage(LogLevel level, String message ){
        this.timestamp = new Date();
        this.logLevel = level;
        this.message = message;
    }

    // now this message in itself is dynamically structured like traceId: UserId: message do not know how this can we handled
    // but whatever the log message format is the datetime and the loglevel is always added in the front of the formated message as well
}
