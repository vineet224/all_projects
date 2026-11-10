# Redis Hashes Module (SDE-2 System Design Deep Dive)

A Redis Hash is an in-memory dictionary mapping string fields to string values under a single key. It represents the canonical data structure in Redis for modeling **domain entities, objects, session records, and partial-update caches**.

---

## 1. Architectural Internals & Encodings

Every Redis Hash is dynamically encoded using one of two internal memory representations:

| Encoding | Criteria | Data Structure | Time Complexity | Memory Characteristics |
| :--- | :--- | :--- | :--- | :--- |
| **`listpack`** (formerly `ziplist`) | Field count $\le 512$ (`hash-max-listpack-entries`) **AND** all fields/values $\le 64$ bytes (`hash-max-listpack-value`) | Contiguous binary byte array containing sequential `(field, value)` pairs | $O(N)$ field lookup, but faster than hashtable for small $N$ due to CPU L1/L2 cache prefetching | **Ultra-compact**. Zero pointer overhead, zero memory fragmentation |
| **`hashtable`** | Entry count $> 512$ **OR** any field/value $> 64$ bytes | MurmurHash2/SipHash dictionary with chaining | $O(1)$ amortized field lookup | Higher memory footprint due to `dictEntry` structs (24 bytes header + pointers per entry) |

> [!IMPORTANT]
> **SDE-2 System Design Rule:**
> If you store 1,000,000 entities as 1,000,000 individual Redis String keys, each key incurs full Redis `dictEntry` + `robj` + SDS memory overhead. By grouping related fields inside a Redis Hash under listpack limits, you can achieve a **$5\times - 10\times$ reduction in overall Redis RAM usage**.

---

## 2. Command Reference Matrix

| Command & Syntax | Complexity | Atomicity | Since | Description & Edge Cases |
| :--- | :--- | :--- | :--- | :--- |
| `HSET key field value [field value ...]` | $O(1)$ per field | Atomic | 2.0.0 | Sets field(s). Returns number of *newly added* fields (updating existing field returns 0). |
| `HGET key field` | $O(1)$ | Atomic | 2.0.0 | Returns field value, or `null` if field or key does not exist. |
| `HMGET key field [field ...]` | $O(N)$ | Atomic | 2.0.0 | Returns array of values. Missing fields return `null` at their array index. |
| `HGETALL key` | $O(N)$ | Atomic | 2.0.0 | Returns all fields and values. **⚠️ Anti-pattern** on high-cardinality hashes (blocks the event loop!). |
| `HSETNX key field value` | $O(1)$ | Atomic | 2.0.0 | Sets field only if it does NOT already exist. Returns 1 (set) or 0 (exists). |
| `HDEL key field [field ...]` | $O(N)$ | Atomic | 2.0.0 | Deletes specified fields. Returns count of removed fields. |
| `HEXISTS key field` | $O(1)$ | Atomic | 2.0.0 | Returns 1 if field exists, 0 otherwise. |
| `HLEN key` | $O(1)$ | Atomic | 2.0.0 | Returns total number of fields in the hash. |
| `HSTRLEN key field` | $O(1)$ | Atomic | 3.2.0 | Returns string byte length of field's value (0 if field missing). |
| `HINCRBY key field increment` | $O(1)$ | Atomic | 2.0.0 | Increments integer field by signed 64-bit integer. Auto-initializes missing field to 0. |
| `HINCRBYFLOAT key field increment` | $O(1)$ | Atomic | 2.6.0 | Increments field by double-precision float. |
| `HSCAN key cursor [MATCH pat] [COUNT n]` | $O(1)$ per call | Atomic | 2.8.0 | Safe cursor-based iteration through hash fields. Production replacement for `HGETALL`. |
| `HEXPIRE key sec FIELDS n f1 [f2 ...]` | $O(N)$ | Atomic | **7.4.0** | Sets TTL on specific fields inside a hash without expiring parent key. |
| `HTTL key FIELDS n f1 [f2 ...]` | $O(N)$ | Atomic | **7.4.0** | Returns TTL in seconds for specific fields inside a hash. |

---

## 3. Production Anti-Pattern: The `HGETALL` Trap

### The Problem:
`HGETALL key` retrieves every field and value in a single command ($O(N)$ time complexity).

If a hash grows to have 50,000 fields (e.g. an e-commerce catalog or user event store):
1. Redis is single-threaded for command execution.
2. `HGETALL` will serialize and transmit 50,000 entries, blocking the event loop for tens or hundreds of milliseconds.
3. Every other concurrent request from all client pods will time out!

### The Solution:
Use `HSCAN` for paginated traversal or `HMGET` when you know the required fields:

```ts
// ✅ Safe non-blocking iteration using HSCAN
let cursor = 0;
do {
  const reply = await client.hScan(hashKey, cursor, { COUNT: 100 });
  cursor = reply.cursor;
  for (const { field, value } of reply.tuples) {
    processField(field, value);
  }
} while (cursor !== 0);
```

---

## 4. SDE-2 System Design Patterns

### Pattern 1: User Profile & Dynamic Session Store
- **Advantage over JSON string:** You can update `lastLoginAt` and increment `loginCount` in a single $O(1)$ `HINCRBY` call without deserializing, mutating, and re-serializing the entire JSON document.
- **Field Expiration (Redis 7.4+):** Store temporary MFA codes or session tokens with their own TTL inside the user profile hash via `HEXPIRE`.

### Pattern 2: Shopping Cart
- Use `HINCRBY cart:userId item:101:qty 1` to adjust quantities.
- When quantity drops to $\le 0$, automatically delete the item via `HDEL`.
- Calculate subtotal in Lua or through selective `HMGET`.

---

## 5. How to Run This Module

```bash
# Run Vitest tests for Hashes
npm run test:hashes

# Run standalone experiments
npm run exp:hashes:encodings    # listpack vs hashtable memory transition
npm run exp:hashes:json-vs-hash  # System design comparison: JSON String vs Hash
npm run exp:hashes:field-expiry  # Redis 7.4+ field-level TTL (HEXPIRE)
```

