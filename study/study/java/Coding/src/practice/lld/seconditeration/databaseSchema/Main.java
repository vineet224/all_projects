package practice.lld.seconditeration.databaseSchema;

import java.util.*;
import java.util.function.Predicate;

/**
 * =======================================================================================
 * LLD INTERVIEW CHEAT SHEET: RELATIONAL DATABASE (TYPE REGISTRY + INDEXING)
 * =======================================================================================
 * 1. FUNCTIONAL REQUIREMENTS:
 * - Create tables with a strict schema (Column Name, String Type, Primary Key).
 * - Insert rows and validate them against registered runtime types.
 * - Retrieve rows by Primary Key or Secondary Index.
 * * 2. NON-FUNCTIONAL REQUIREMENTS:
 * - Extensibility (OCP): Types must be dynamically injectable without touching core code.
 * - Read Latency: Queries on indexed columns must be O(1) via inverted hashing.
 * * 3. ASSUMPTIONS:
 * - Single-threaded for this specific iteration (to focus strictly on schema/indexing).
 * - Types are strictly checked via Java `instanceof` inside the Predicates.
 * * 4. HLD CALLOUTS (Drop these verbally to show seniority):
 * - Type Registry Pattern: "I'm using a Map of Predicates instead of an Enum. In a
 * real engine, this allows us to inject custom user-defined types (like PostGIS geometries
 * or JSONB) at runtime without recompiling the database core."
 * - B-Trees vs Hash Indexes: "I implemented a Hash Index for O(1) exact matches. In a
 * real DB (like Postgres), this would be a B-Tree to support range queries (age > 25)."
 * * 5. SKIPPED FOR TIME / PRIORITY CONSTRAINTS:
 * - SQL Parser (AST generation), Foreign Key Constraints, and Compound Indexes.
 * =======================================================================================
 */

// --- 1. The Strategy Pattern: Runtime Type Registry ---

class TypeRegistry {
    private static final Map<String, Predicate<Object>> validators = new HashMap<>();

    // Register basic types on boot
    static {
        validators.put("STRING", val -> val instanceof String);
        validators.put("INTEGER", val -> val instanceof Integer);
        validators.put("BOOLEAN", val -> val instanceof Boolean);

        // Example of a complex, domain-specific type added easily
        validators.put("EMAIL", val -> val instanceof String && ((String) val).contains("@"));
    }

    // Exposed so users/plugins can inject custom types at runtime
    public static void registerType(String typeName, Predicate<Object> validator) {
        validators.put(typeName.toUpperCase(), validator);
    }

    public static boolean validate(String type, Object value) {
        Predicate<Object> validator = validators.get(type.toUpperCase());
        if (validator == null) throw new IllegalArgumentException("Unsupported Database Type: " + type);
        return validator.test(value);
    }
}

class Column {
    final String name;
    final String typeString; // Replaced Enum with String to link to TypeRegistry
    final boolean isPrimaryKey;
    final boolean isIndexed;

    public Column(String name, String typeString, boolean isPrimaryKey, boolean isIndexed) {
        this.name = name;
        this.typeString = typeString.toUpperCase();
        this.isPrimaryKey = isPrimaryKey;
        this.isIndexed = isIndexed;
    }
}

// --- 2. Core Table Engine ---

class Table {
    private final String tableName;
    private final Map<String, Column> schema = new HashMap<>();
    private String primaryKeyColumn;

    // SOURCE OF TRUTH: Primary_Key -> Row_Data
    private final Map<Object, Map<String, Object>> rows = new HashMap<>();

    // SECONDARY INDEXES: Column_Name -> (Column_Value -> Set_of_PKs)
    private final Map<String, Map<Object, Set<Object>>> indexes = new HashMap<>();

    public Table(String tableName, List<Column> columns) {
        this.tableName = tableName;
        for (Column col : columns) {
            schema.put(col.name, col);
            if (col.isPrimaryKey) {
                if (primaryKeyColumn != null) throw new IllegalArgumentException("Multiple PKs not allowed");
                this.primaryKeyColumn = col.name;
            }
            if (col.isIndexed && !col.isPrimaryKey) {
                indexes.put(col.name, new HashMap<>());
            }
        }
        if (primaryKeyColumn == null) throw new IllegalArgumentException("Table must have a Primary Key");
    }

    /**
     * CORE SIGNAL 1: Dynamic Schema Validation & Atomic Updates
     */
    public void insert(Map<String, Object> rowData) {
        // 1. Check Primary Key
        Object pkValue = rowData.get(primaryKeyColumn);
        if (pkValue == null) throw new IllegalArgumentException("Missing Primary Key: " + primaryKeyColumn);
        if (rows.containsKey(pkValue)) throw new IllegalArgumentException("Duplicate PK: " + pkValue);

        // 2. Validate Schema using the Runtime Registry
        for (Map.Entry<String, Object> entry : rowData.entrySet()) {
            Column col = schema.get(entry.getKey());
            if (col == null) throw new IllegalArgumentException("Unknown column: " + entry.getKey());

            if (!TypeRegistry.validate(col.typeString, entry.getValue())) {
                throw new IllegalArgumentException("Type mismatch for '" + col.name + "'. Expected " + col.typeString);
            }
        }

        // 3. Commit Row
        rows.put(pkValue, new HashMap<>(rowData));

        // 4. Update Secondary Indexes
        for (String colName : indexes.keySet()) {
            Object colValue = rowData.get(colName);
            if (colValue != null) {
                indexes.get(colName)
                        .computeIfAbsent(colValue, k -> new HashSet<>())
                        .add(pkValue);
            }
        }
        System.out.println("✅ Inserted into " + tableName + ": " + rowData);
    }

    /**
     * CORE SIGNAL 2: Index-Aware Querying
     */
    public List<Map<String, Object>> getByColumn(String colName, Object value) {
        if (!schema.containsKey(colName)) throw new IllegalArgumentException("Invalid column");

        List<Map<String, Object>> result = new ArrayList<>();

        if (colName.equals(primaryKeyColumn)) {
            Map<String, Object> row = rows.get(value);
            if (row != null) result.add(row);
            return result;
        }

        if (indexes.containsKey(colName)) {
            System.out.println("⚡ Using Hash Index for [" + colName + "]");
            Set<Object> pkSet = indexes.get(colName).getOrDefault(value, Collections.emptySet());
            for (Object pk : pkSet) {
                result.add(rows.get(pk));
            }
            return result;
        }

        System.out.println("🐢 Performing Full Table Scan for [" + colName + "]");
        for (Map<String, Object> row : rows.values()) {
            if (value.equals(row.get(colName))) {
                result.add(row);
            }
        }
        return result;
    }
}

// --- 3. Database Orchestrator & Test Execution ---

class Database {
    private final Map<String, Table> tables = new HashMap<>();

    public void createTable(String name, List<Column> columns) {
        tables.put(name, new Table(name, columns));
        System.out.println("🛠️ Created table: " + name);
    }

    public Table getTable(String name) {
        return tables.get(name);
    }
}

public class Main {
    public static void main(String[] args) {
        Database db = new Database();

        // 1. Define Schema using our dynamic String types
        List<Column> userCols = Arrays.asList(
                new Column("id", "INTEGER", true, true),       // PK
                new Column("email", "EMAIL", false, true),     // Indexed custom type!
                new Column("age", "INTEGER", false, false)     // Not indexed
        );
        db.createTable("Users", userCols);
        Table users = db.getTable("Users");

        System.out.println("\n=== SCENARIO 1: VALID INSERTS ===");
        users.insert(Map.of("id", 1, "email", "alice@corp.com", "age", 25));
        users.insert(Map.of("id", 2, "email", "bob@corp.com", "age", 30));

        System.out.println("\n=== SCENARIO 2: RUNTIME TYPE FAILURES ===");
        try {
            // Will fail because the TypeRegistry's "EMAIL" predicate requires an '@' symbol
            users.insert(Map.of("id", 3, "email", "invalid-email-format", "age", 25));
        } catch (Exception e) { System.out.println("❌ Blocked: " + e.getMessage()); }

        try {
            users.insert(Map.of("id", 4, "email", "dave@corp.com", "age", "TWENTY")); // String instead of Int
        } catch (Exception e) { System.out.println("❌ Blocked: " + e.getMessage()); }

        System.out.println("\n=== SCENARIO 3: QUERY EXECUTION PLAN ===");
        // O(1) Lookup
        System.out.println(users.getByColumn("email", "bob@corp.com"));

        // O(N) Table Scan
        System.out.println(users.getByColumn("age", 25));
    }
}