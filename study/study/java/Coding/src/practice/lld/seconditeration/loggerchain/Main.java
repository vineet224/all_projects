package practice.lld.seconditeration.loggerchain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * =======================================================================================
 * LLD INTERVIEW CHEAT SHEET: ASYNC LOGGER FRAMEWORK (Log4j clone)
 * =======================================================================================
 * 1. FUNCTIONAL REQUIREMENTS:
 * - Support multiple log levels (INFO, DEBUG, ERROR).
 * - Support multiple appenders (Console, File, Database).
 * - Route logs based on severity (Chain of Responsibility).
 * * 2. NON-FUNCTIONAL REQUIREMENTS:
 * - Thread-Safety: Multiple application threads must be able to log simultaneously.
 * - Low Latency: Logging MUST NOT block the main application threads (Asynchronous).
 * * 3. ASSUMPTIONS:
 * - Global Singleton access is acceptable for the Logger instance.
 * - File/DB Appenders are mocked to Console to avoid boilerplate I/O exception handling.
 * * 4. HLD CALLOUTS (Drop these verbally to show seniority):
 * - Log Aggregation: "In production, logs wouldn't just sit in a file. I'd use an agent
 * like Fluentd or Logstash to ship these logs asynchronously to ElasticSearch/Kibana."
 * - Queue Overflow: "If the system produces logs faster than the disk can write, the
 * in-memory queue will OOM. I'd use a bounded RingBuffer (like LMAX Disruptor used in Log4j2)
 * and drop INFO/DEBUG logs if the buffer hits 95% capacity to save the application."
 * - File Rotation: "File appenders would need rotation policies (Size-based or Time-based)
 * to prevent filling up the server's disk space."
 * * 5. SKIPPED FOR TIME / PRIORITY CONSTRAINTS:
 * - Actual File I/O (`BufferedWriter`), Configuration parsing (XML/Properties),
 * and advanced formatting patterns (Regex/String interpolation).
 * =======================================================================================
 */

// --- 1. Enums & Models ---

enum LogLevel {
    INFO(1), DEBUG(2), ERROR(3);
    final int severity;
    LogLevel(int severity) { this.severity = severity; }
}

class LogMessage {
    final LogLevel level;
    final String message;
    final String threadName;
    final LocalDateTime timestamp;

    public LogMessage(LogLevel level, String message, String threadName) {
        this.level = level;
        this.message = message;
        this.threadName = threadName;
        this.timestamp = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "[" + timestamp + "] [" + threadName + "] [" + level + "] : " + message;
    }
}

// --- 2. Observer/Strategy Pattern: Appenders ---

interface LogAppender {
    void append(LogMessage message);
}

class ConsoleAppender implements LogAppender {
    @Override
    public void append(LogMessage message) {
        // Synchronized to prevent console text overlapping from different handlers
        synchronized (System.out) {
            System.out.println(message.toString());
        }
    }
}

class FileAppender implements LogAppender {
    @Override
    public void append(LogMessage message) {
        // Mocked: Pretend this is writing to a file (e.g., application.log)
        synchronized (System.out) {
            System.out.println("📄 [FILE] " + message.toString());
        }
    }
}

// --- 3. Chain of Responsibility Pattern: Handlers ---

abstract class LogHandler {
    protected LogLevel handlingLevel;
    protected LogHandler nextHandler;
    protected List<LogAppender> appenders = new ArrayList<>();

    public LogHandler(LogLevel level) {
        this.handlingLevel = level;
    }

    public void setNextHandler(LogHandler nextHandler) {
        this.nextHandler = nextHandler;
    }

    public void addAppender(LogAppender appender) {
        this.appenders.add(appender);
    }

    public void processLog(LogMessage logMessage) {
        // If the incoming log severity is >= this handler's severity, process it.
        if (logMessage.level.severity >= this.handlingLevel.severity) {
            for (LogAppender appender : appenders) {
                appender.append(logMessage);
            }
        }
        // Always pass it down the chain (e.g., An ERROR log goes to Info, Debug, and Error handlers)
        if (nextHandler != null) {
            nextHandler.processLog(logMessage);
        }
    }
}

class InfoHandler extends LogHandler { public InfoHandler() { super(LogLevel.INFO); } }
class DebugHandler extends LogHandler { public DebugHandler() { super(LogLevel.DEBUG); } }
class ErrorHandler extends LogHandler { public ErrorHandler() { super(LogLevel.ERROR); } }

// --- 4. Core System: Async Singleton Logger ---

class LoggerManager {
    private static volatile LoggerManager instance;
    private LogHandler chainRoot;

    // CORE SIGNAL: Asynchronous logging to prevent blocking App threads
    private final BlockingQueue<LogMessage> logQueue;
    private volatile boolean isRunning;

    private LoggerManager() {
        this.logQueue = new LinkedBlockingQueue<>(); // Unbounded for interviews.interview, Bounded in prod
        this.isRunning = true;
        buildChain();
        startAsyncWorker();
    }

    // Double-Checked Locking Singleton
    public static LoggerManager getInstance() {
        if (instance == null) {
            synchronized (LoggerManager.class) {
                if (instance == null) {
                    instance = new LoggerManager();
                }
            }
        }
        return instance;
    }

    private void buildChain() {
        LogHandler info = new InfoHandler();
        LogHandler debug = new DebugHandler();
        LogHandler error = new ErrorHandler();

        // Chain: INFO -> DEBUG -> ERROR
        info.setNextHandler(debug);
        debug.setNextHandler(error);
        this.chainRoot = info;

        // Configuration: INFO logs to Console, ERROR logs to File & Console
        info.addAppender(new ConsoleAppender());
        error.addAppender(new FileAppender());
    }

    private void startAsyncWorker() {
        Thread worker = new Thread(() -> {
            while (isRunning || !logQueue.isEmpty()) {
                try {
                    // pull logs from the queue. Blocks if empty to save CPU.
                    LogMessage msg = logQueue.take();
                    chainRoot.processLog(msg);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "Async-Log-Worker");
        worker.setDaemon(true); // Don't prevent JVM shutdown
        worker.start();
    }

    // The API exposed to the Application
    public void info(String message) { publish(LogLevel.INFO, message); }
    public void debug(String message) { publish(LogLevel.DEBUG, message); }
    public void error(String message) { publish(LogLevel.ERROR, message); }

    private void publish(LogLevel level, String message) {
        LogMessage logMsg = new LogMessage(level, message, Thread.currentThread().getName());
        logQueue.offer(logMsg); // O(1) Non-blocking push to queue
    }

    public void shutdown() {
        this.isRunning = false;
    }
}

// --- 5. Main Execution & Concurrency Test ---

public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== INITIALIZING ASYNC LOGGER ===\n");
        LoggerManager log = LoggerManager.getInstance();

        // 1. Basic Routing Test
        log.info("System booted successfully."); // Should go to Console
        log.error("Database connection failed!"); // Should go to Console AND File

        // Wait a bit to let the async worker catch up before hitting it with threads
        Thread.sleep(100);

        System.out.println("\n=== CONCURRENCY TEST: MULTIPLE THREADS ===");

        // Spin up 3 application threads spamming logs simultaneously.
        // Because of our Async Queue, their execution time will be near 0ms,
        // as they aren't waiting for the slow Console/File I/O.
        Runnable dbTask = () -> log.debug("Fetching user records...");
        Runnable apiTask = () -> log.info("Received HTTP 200 GET /users");
        Runnable bgTask = () -> log.error("Background job crashed at 99%");

        Thread t1 = new Thread(dbTask, "App-DB-Thread");
        Thread t2 = new Thread(apiTask, "App-API-Thread");
        Thread t3 = new Thread(bgTask, "App-Cron-Thread");

        t1.start(); t2.start(); t3.start();
        t1.join(); t2.join(); t3.join();

        // Give the background Log-Worker thread time to empty the queue and print
        Thread.sleep(1000);
        log.shutdown();
        System.out.println("\nSystem Shut Down. Notice how the application threads finished instantly while the logger worked in the background.");
    }
}
