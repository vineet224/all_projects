package practice.lld.seconditeration.loggerFilter;


import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * =======================================================================================
 * LLD INTERVIEW CHEAT SHEET: LOG QUERY ENGINE (MAP EXTRACTORS + SINGLE PASS O(N))
 * =======================================================================================
 * 1. FUNCTIONAL REQUIREMENTS:
 * - Query logs with N filters, dynamic sorting, and grouping.
 * * 2. NON-FUNCTIONAL REQUIREMENTS:
 * - Thread-Safety: Readers and writers must not block each other or crash.
 * - Extensibility (OCP): Adding a new field must not change the query logic.
 * - Memory Efficiency: Filter N conditions in a single pass to avoid GC spikes.
 * * 3. HLD CALLOUTS (Drop these verbally):
 * - Extractor Map: "I am using a Map of Functional Interfaces. This is a Strategy Pattern.
 * If we add a 'UserId' field later, I just register a new extractor in the Map constructor,
 * and the query engine instantly supports filtering, sorting, and grouping by UserId."
 * - Thread Safety: "Using CopyOnWriteArrayList ensures that background threads dumping
 * new logs won't throw ConcurrentModificationExceptions for users running queries."
 * =======================================================================================
 */

enum LogLevel { INFO, DEBUG, ERROR }

class LogMessage {
    final String logId;
    final String traceId;
    final String containerId;
    final long timestamp;
    final LogLevel level;
    final String message;

    public LogMessage(String logline) {
        String[] split = logline.split("\\|");
        this.logId = UUID.randomUUID().toString().substring(0, 8);
        this.traceId = split[0];
        this.containerId = split[1];
        this.timestamp = Long.parseLong(split[1]);
        this.level = LogLevel.valueOf(split[2]);
        this.message = split[3];
    }

    @Override
    public String toString() {
        return String.format("[%s] %s | %s | %s", level, containerId, traceId, message);
    }
}

class FilterQuery {
    final String key;
    final String value;

    public FilterQuery(String key, String value) {
        this.key = key;
        this.value = value;
    }
}

// --- Core Query Engine ---

class LoggerManager {
    private final List<LogMessage> logStore = new CopyOnWriteArrayList<>();

    // CORE SIGNAL: Dynamic Field Extractors. OCP Compliant.
    private final Map<String, Function<LogMessage, String>> extractors = new HashMap<>();

    public LoggerManager() {
        // Registering our fields. Easy to expand without touching the core logic.
        extractors.put("traceId", log -> log.traceId);
        extractors.put("containerId", log -> log.containerId);
        extractors.put("level", log -> log.level.name());
        extractors.put("timestamp", log -> String.valueOf(log.timestamp));
    }

    public void addLog(String logLine) {
        try {
            logStore.add(new LogMessage(logLine));
        } catch (Exception e) {
            System.err.println("Dropped malformed log: " + logLine);
        }
    }

    /**
     * CORE SIGNAL: Single-Pass Filtering without Java Streams.
     */
    public Map<String, List<LogMessage>> getLogs(
            List<FilterQuery> filters,
            String sortKey,
            String groupKey) {

        List<LogMessage> filteredResults = new ArrayList<>();

        // 1. SINGLE-PASS FILTERING (Memory Efficient)
        for (LogMessage log : logStore) {
            boolean matchesAll = true;

            if (filters != null) {
                for (FilterQuery filter : filters) {
                    Function<LogMessage, String> extractor = extractors.get(filter.key);

                    // If key is invalid or value doesn't match, drop the log
                    if (extractor == null || !extractor.apply(log).equals(filter.value)) {
                        matchesAll = false;
                        break;
                    }
                }
            }

            if (matchesAll) {
                filteredResults.add(log);
            }
        }

        // 2. DYNAMIC SORTING
        if (sortKey != null && extractors.containsKey(sortKey)) {
            Function<LogMessage, String> sortExtractor = extractors.get(sortKey);

            Collections.sort(filteredResults, new Comparator<LogMessage>() {
                @Override
                public int compare(LogMessage a, LogMessage b) {
                    return sortExtractor.apply(a).compareTo(sortExtractor.apply(b));
                }
            });
        }

        // 3. DYNAMIC GROUPING
        Map<String, List<LogMessage>> groupedResults = new HashMap<>();

        if (groupKey != null && extractors.containsKey(groupKey)) {
            Function<LogMessage, String> groupExtractor = extractors.get(groupKey);

            for (LogMessage log : filteredResults) {
                String keyVal = groupExtractor.apply(log);
                groupedResults.putIfAbsent(keyVal, new ArrayList<>());
                groupedResults.get(keyVal).add(log);
            }
        } else {
            groupedResults.put("ALL_RESULTS", filteredResults);
        }

        return groupedResults;
    }
}

// --- Main Execution ---

public class Main {
    public static void main(String[] args) {
        LoggerManager logger = new LoggerManager();

        logger.addLog("TRC-01|CONT-A|1600000001|INFO|Server started");
        logger.addLog("TRC-01|CONT-A|1600000002|DEBUG|Loading configs");
        logger.addLog("TRC-02|CONT-B|1600000003|ERROR|DB Timeout");
        logger.addLog("TRC-01|CONT-B|1600000004|INFO|Retrying request");

        System.out.println("=== QUERY 1: Filter by TRC-01, Sort by Timestamp, Group by Container ===\n");
        List<FilterQuery> filters = new ArrayList<>();
        filters.add(new FilterQuery("traceId", "TRC-01"));

        Map<String, List<LogMessage>> result1 = logger.getLogs(filters, "timestamp", "containerId");
        printResults(result1);

        System.out.println("\n=== QUERY 2: Filter by INFO, Group by traceId ===\n");
        List<FilterQuery> filters2 = new ArrayList<>();
        filters2.add(new FilterQuery("level", "INFO"));

        Map<String, List<LogMessage>> result2 = logger.getLogs(filters2, null, "traceId");
        printResults(result2);
    }

    private static void printResults(Map<String, List<LogMessage>> map) {
        for (Map.Entry<String, List<LogMessage>> entry : map.entrySet()) {
            System.out.println("GROUP: [" + entry.getKey() + "]");
            for (LogMessage log : entry.getValue()) {
                System.out.println("   -> " + log);
            }
        }
    }
}

/**
 * =======================================================================================
 * LLD INTERVIEW CHEAT SHEET: LOG AGGREGATOR & QUERY ENGINE
 * =======================================================================================
 * 1. FUNCTIONAL REQUIREMENTS:
 * - Ingest logs with structured fields (TraceId, ContainerId, Level, Message).
 * - Query logs with N filters, dynamic sorting, and 1-level grouping.
 * * 2. NON-FUNCTIONAL REQUIREMENTS:
 * - Thread-Safety: High volume of writes MUST NOT crash concurrent read queries.
 * - Extensibility (OCP): Adding a new log field must not require rewriting the query engine.
 * * 3. ASSUMPTIONS:
 * - Memory can hold the recent log window.
 * - Grouping always returns a Map<String, List<LogMessage>>.
 * * 4. HLD CALLOUTS (Drop these verbally to show seniority):
 * - Inverted Indexing: "In this code, queries do a linear O(N) scan. In a real system
 * like ElasticSearch, I would build an Inverted Index (Map<String, Set<LogId>>) for
 * O(1) lookups on frequently filtered fields like traceId."
 * - Log Retention: "To prevent OOM, I would wrap the in-memory store in a Circular
 * Buffer (Ring Buffer) or a Time-To-Live (TTL) eviction thread."
 * * 5. SKIPPED FOR TIME / PRIORITY CONSTRAINTS:
 * - Async Sinks: Skipped printing to files/consoles to focus entirely on the Query Engine.
 * - Complex Filters: Assuming exact match (Equals) for time constraints, skipping regex/LIKE.
 * =======================================================================================
 */

