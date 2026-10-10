package practice.lld.logging;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * functional requirements
 * - multiple log levels
 * - async logging
 *
 * nonfunctional requirements
 * - threadsafe logging
 */

enum LogLevel { DEBUG, INFO, ERROR }

class Logmessage{
    String traceId;
    String message;
    String localdatetime;
    LogLevel level;
    public Logmessage(String traceId, String message, String localdatetime, LogLevel level) {
        this.traceId = traceId;
        this.message = message;
        this.localdatetime = localdatetime;
        this.level = level;
    }
    @Override
    public String toString() {
        return (this.traceId + ":" + this.message + ":" + this.localdatetime + ":" + this.level);
    }
}

interface LogAppender{
    public void appendLog(Logmessage message);
}

class ConsoleAppender implements LogAppender{
    private final PriorityBlockingQueue<Logmessage> queue =  new PriorityBlockingQueue<>();
    ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public void appendLog(Logmessage message) {
        queue.offer(message);
    }

    public void start(){
        executorService.submit(()->{
            while(true){
                Logmessage message = queue.take();
                System.out.println("process the message : "+ message.toString());
            }
        });
    }
}

class FileAppender implements LogAppender{
    private final PriorityBlockingQueue<Logmessage> queue =  new PriorityBlockingQueue<>();
    ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public void appendLog(Logmessage message) {
        queue.offer(message);
    }
}

public class LoggingManager {

    HashMap<LogLevel, ArrayList<LogAppender>> appendersPerLevel = new HashMap<>();
    PriorityBlockingQueue<Logmessage> queue = new PriorityBlockingQueue<>();
    ExecutorService executorService = Executors.newSingleThreadExecutor();


    public void logMessage(LogLevel level, String message, String traceId){
        LocalDateTime  now = LocalDateTime.now();
        Logmessage logmessage = new Logmessage(traceId,message,now.toString(),level);
        queue.offer(logmessage);
    }

    public void start(){
        executorService.submit(()->{
            while(true){
                Logmessage message = queue.take();
                ArrayList<LogAppender> appenders = appendersPerLevel.get(message.level);
                for(LogAppender appender : appenders){
                    appender.appendLog(message);
                }
            }
        });
    }
}
