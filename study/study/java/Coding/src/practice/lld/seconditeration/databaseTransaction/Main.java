package practice.lld.seconditeration.databaseTransaction;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * =======================================================================================
 * LLD INTERVIEW CHEAT SHEET: IN-MEMORY DATABASE (KEY-VALUE + TRANSACTIONS)
 * =======================================================================================
 * 1. FUNCTIONAL REQUIREMENTS:
 * - Basic CRUD: get(key), put(key, value), delete(key).
 * - Transactions: begin(), commit(), rollback().
 * - Support nested transactions (e.g., Begin -> Begin -> Rollback -> Commit).
 * * 2. NON-FUNCTIONAL REQUIREMENTS:
 * - Isolation: A transaction in Thread A MUST NOT see uncommitted changes from Thread B.
 * - Thread-Safety: Committing to the global store must be atomic to prevent race conditions.
 * * 3. ASSUMPTIONS:
 * - In-memory only. Key and Value are Strings.
 * - Transactions are bound to the current executing thread (ThreadLocal).
 * * 4. HLD CALLOUTS (Drop these verbally to show seniority):
 * - Durability (WAL): "Since this is in-memory, if the JVM crashes, data is lost. In
 * production, every `commit()` would append to an append-only Write-Ahead Log (WAL) on
 * disk before returning success to the user."
 * - MVCC (Multi-Version Concurrency Control): "I'm using ThreadLocal Stacks to isolate
 * uncommitted reads. In a real DB like Postgres, MVCC uses hidden transaction IDs and
 * row versioning so readers never block writers."
 * - B-Trees vs LSM: "For the global store, I'm using a Hash Map (O(1)). If we needed
 * range queries (e.g., `WHERE age > 20`), I'd use a ConcurrentSkipListMap (which acts
 * like a B-Tree/LSM memtable providing O(log N) ordered access)."
 * * 5. SKIPPED FOR TIME / PRIORITY CONSTRAINTS:
 * - File I/O persistence, Secondary Indexes (searching by value), and Deadlock detection
 * (since our ThreadLocal isolation avoids locking on reads).
 * =======================================================================================
 */

class DatabaseManager {

    // Global Store: Source of truth for all committed data.
    private final Map<String, String> globalStore = new ConcurrentHashMap<>();

    // CORE SIGNAL: ThreadLocal Stack for Transaction Isolation.
    // Each thread gets its own private stack of changes. Thread A cannot see Thread B's stack.
    private final ThreadLocal<Stack<Map<String, String>>> transactionStack = new ThreadLocal<>();

    // Tombstone marker to handle deletions within an uncommitted transaction
    private static final String TOMBSTONE = "<DELETED>";

    /**
     * Starts a new transaction. If already in a transaction, creates a nested scope.
     */
    public void begin() {
        Stack<Map<String, String>> stack = transactionStack.get();
        if (stack == null) {
            stack = new Stack<>();
            transactionStack.set(stack);
        }
        // Push a fresh map for this transaction scope
        stack.push(new HashMap<>());
        System.out.println("[" + Thread.currentThread().getName() + "] BEGIN Transaction");
    }

    /**
     * Rolls back the current transaction scope.
     */
    public void rollback() {
        Stack<Map<String, String>> stack = transactionStack.get();
        if (stack == null || stack.isEmpty()) {
            throw new IllegalStateException("No active transaction to rollback.");
        }
        stack.pop();
        if (stack.isEmpty()) {
            transactionStack.remove(); // Prevent memory leaks
        }
        System.out.println("[" + Thread.currentThread().getName() + "] ROLLBACK Transaction");
    }

    /**
     * Commits the current transaction.
     * If nested, merges to the parent transaction.
     * If top-level, atomically merges to the global store.
     */
    public void commit() {
        Stack<Map<String, String>> stack = transactionStack.get();
        if (stack == null || stack.isEmpty()) {
            throw new IllegalStateException("No active transaction to commit.");
        }

        Map<String, String> localChanges = stack.pop();

        if (stack.isEmpty()) {
            // Top-level commit: Merge to global store atomically
            synchronized (globalStore) {
                for (Map.Entry<String, String> entry : localChanges.entrySet()) {
                    if (entry.getValue().equals(TOMBSTONE)) {
                        globalStore.remove(entry.getKey());
                    } else {
                        globalStore.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            transactionStack.remove();
            System.out.println("[" + Thread.currentThread().getName() + "] COMMIT to Global Store");
        } else {
            // Nested commit: Merge into the parent transaction's map
            stack.peek().putAll(localChanges);
            System.out.println("[" + Thread.currentThread().getName() + "] COMMIT to Parent Transaction");
        }
    }

    public void put(String key, String value) {
        Stack<Map<String, String>> stack = transactionStack.get();
        if (stack != null && !stack.isEmpty()) {
            stack.peek().put(key, value); // Write to local transaction
        } else {
            globalStore.put(key, value); // Write directly to global
        }
    }

    public void delete(String key) {
        Stack<Map<String, String>> stack = transactionStack.get();
        if (stack != null && !stack.isEmpty()) {
            stack.peek().put(key, TOMBSTONE); // Mark as deleted locally
        } else {
            globalStore.remove(key); // Delete directly globally
        }
    }

    public String get(String key) {
        Stack<Map<String, String>> stack = transactionStack.get();

        // 1. Search local transactions from newest (top) to oldest (bottom)
        if (stack != null && !stack.isEmpty()) {
            for (int i = stack.size() - 1; i >= 0; i--) {
                Map<String, String> localScope = stack.get(i);
                if (localScope.containsKey(key)) {
                    String val = localScope.get(key);
                    return val.equals(TOMBSTONE) ? null : val;
                }
            }
        }

        // 2. Fallback to global store
        return globalStore.get(key);
    }
}

// --- Main Execution & Test ---

public class Main {
    public static void main(String[] args) throws InterruptedException {
        DatabaseManager db = new DatabaseManager();

        System.out.println("=== TEST 1: BASIC TRANSACTIONS & ROLLBACK ===");
        db.put("A", "10");
        db.begin();
        db.put("A", "20");
        db.put("B", "30");
        System.out.println("Inside TX, A = " + db.get("A")); // Should be 20
        db.rollback();
        System.out.println("After Rollback, A = " + db.get("A")); // Should be 10
        System.out.println("After Rollback, B = " + db.get("B")); // Should be null

        System.out.println("\n=== TEST 2: NESTED TRANSACTIONS & DELETION ===");
        db.begin();
        db.put("C", "100");
        db.begin(); // Nested
        db.put("C", "200");
        db.delete("A");
        System.out.println("Inside Nested TX, C = " + db.get("C")); // 200
        System.out.println("Inside Nested TX, A = " + db.get("A")); // null (Tombstone)
        db.commit(); // Merges to parent TX
        System.out.println("Inside Parent TX, C = " + db.get("C")); // 200
        db.commit(); // Merges to Global
        System.out.println("Global Store, C = " + db.get("C")); // 200
        System.out.println("Global Store, A = " + db.get("A")); // null

        System.out.println("\n=== TEST 3: CONCURRENCY & ISOLATION ===");
        db.put("Shared", "Init");

        Thread t1 = new Thread(() -> {
            db.begin();
            db.put("Shared", "Thread1_Mod");
            try { Thread.sleep(500); } catch (Exception e){}
            // Thread 1's uncommitted change shouldn't be visible to Main thread
            System.out.println("[Thread 1] Shared = " + db.get("Shared"));
            db.commit();
        }, "Worker-1");

        t1.start();
        Thread.sleep(100); // Let t1 start its transaction
        System.out.println("[Main Thread] Shared before T1 commit = " + db.get("Shared")); // Should be "Init"
        t1.join();
        System.out.println("[Main Thread] Shared after T1 commit = " + db.get("Shared")); // Should be "Thread1_Mod"
    }
}
