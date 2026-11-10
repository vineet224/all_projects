# Redis Strings Module (SDE-2 System Design Deep Dive)

Redis Strings are the fundamental data structure in Redis. Despite the name "String", a Redis string is a **binary-safe byte array** capable of holding up to **512 MB** of arbitrary data: raw text, JSON payloads, serialized Protocol Buffers, 64-bit integers, floating-point numbers, or raw binary bitmap streams.

---

## 1. Architectural Internals: Simple Dynamic Strings (SDS)

Redis does not use standard C null-terminated character arrays (`char*`). Instead, it implements a custom data structure called **Simple Dynamic String (SDS)**.

### SDS Structure Layout
In modern Redis (v3.2+ through v8.x), SDS uses header types optimized for length (`sdshdr8`, `sdshdr16`, `sdshdr32`, `sdshdr64`):

```c
struct __attribute__ ((__packed__)) sdshdr8 {
    uint8_t len;         /* Number of bytes currently used (O(1) STRLEN) */
    uint8_t alloc;       /* Total allocated memory excluding header & null terminator */
    unsigned char flags; /* 3 bits for header type, 5 bits unused */
    char buf[];          /* Actual byte array + trailing '\0' for libc compatibility */
};
```

### Why SDS over Standard C Strings?
1. **$O(1)$ Length Retrieval**: `STRLEN` reads `hdr->len` in constant time without scanning the string ($O(N)$ in C).
2. **Buffer Overflow Prevention**: Redis checks remaining allocation (`alloc - len`) before writing. If space is insufficient, it reallocates automatically.
3. **Binary Safety**: Standard C strings treat `\0` (null byte) as the end of a string. SDS uses the explicit `len` field, permitting arbitrary binary data (images, compressed objects, null-delimited bitfields).
4. **Memory Allocation Reduction**:
   - **Pre-allocation**: When growing, Redis allocates unused memory (`alloc > len`) to amortize subsequent append operations.
   - **Lazy Deallocation**: When shortening strings (e.g. `TRIM`), memory is preserved for future writes rather than instantly freed.

---

## 2. Object Encodings: `int`, `embstr`, and `raw`

Every Redis value is wrapped in a `redisObject` header:

```c
typedef struct redisObject {
    unsigned type:4;       /* OBJ_STRING */
    unsigned encoding:4;   /* OBJ_ENCODING_RAW, OBJ_ENCODING_INT, OBJ_ENCODING_EMBSTR */
    unsigned lru:24;       /* LRU / LFU eviction metadata */
    int refcount;          /* Reference count */
    void *ptr;             /* Pointer to actual data or SDS struct */
} robj;
```

Redis dynamically selects the most compact encoding for Strings:

| Encoding | Criteria | Memory Layout | Allocation Count | Mutability |
| :--- | :--- | :--- | :--- | :--- |
| **`int`** | Numeric string fitting in 64-bit signed integer | Value stored directly in `robj.ptr` (no SDS struct!) | **0** extra allocations | Mutable via `INCR`/`DECR` |
| **`embstr`** | Short string (total object + SDS $\le 64$ bytes) | `redisObject` + `sdshdr8` allocated in **1 continuous memory chunk** | **1** jemalloc allocation | **Immutable** (mutations convert to `raw`) |
| **`raw`** | Strings exceeding the continuous allocation threshold | `redisObject` points to an independent `sdshdr` chunk | **2** distinct jemalloc allocations | Mutable in place |

> [!IMPORTANT]
> **SDE-2 Interview Insight:**
> `embstr` is immutable. If you execute `APPEND` or `SETRANGE` on an `embstr` string, Redis will unconditionally re-allocate it into a `raw` string. If your system frequently mutates cached values, prefer updating full payloads or using Hashes.

---

## 3. Exhaustive Command Reference

### A. Basic Read & Write

| Command & Syntax | Complexity | Atomicity | Since | Description & Edge Cases |
| :--- | :--- | :--- | :--- | :--- |
| `SET key value [NX\|XX] [GET] [EX s\|PX ms\|KEEPTTL]` | $O(1)$ | Atomic | 1.0.0 (options 2.6.12+) | Sets string value with conditional and expiration options. Returns `"OK"`, old value (if `GET`), or `null` (if conditional fails). |
| `GET key` | $O(1)$ | Atomic | 1.0.0 | Returns string value, or `null` if key does not exist. Throws `WRONGTYPE` on non-string keys. |
| `MSET key value [key value ...]` | $O(N)$ | Atomic | 1.0.1 | Sets multiple keys in a single atomic transaction. All keys are updated before any client can read intermediate states. |
| `MGET key [key ...]` | $O(N)$ | Atomic | 1.0.0 | Returns array of values. Non-existent keys or wrong-type keys return `null` in their respective array indices without aborting the batch. |
| `GETDEL key` | $O(1)$ | Atomic | 6.2.0 | Atomically returns the value and deletes the key in one network round-trip. |
| `GETEX key [EX s\|PX ms\|PERSIST]` | $O(1)$ | Atomic | 6.2.0 | Atomically gets the value and modifies/clears TTL. Useful for rolling sessions. |

### B. Conditional Mutations

| Command & Syntax | Complexity | Atomicity | Description & System Design Context |
| :--- | :--- | :--- | :--- |
| `SET key value NX` | $O(1)$ | Atomic | Only sets value if key does not exist. Primary building block for **distributed locks** and **idempotency keys**. |
| `SET key value XX` | $O(1)$ | Atomic | Only sets value if key already exists. Used for updating pre-existing cache entries without creating ghost records. |
| `MSETNX key value [key value ...]` | $O(N)$ | Atomic | **All-or-nothing**: Sets all keys only if **NONE** of the specified keys already exist. If even one key exists, zero keys are set. |

### C. Numeric & Counter Operations

| Command & Syntax | Complexity | Atomicity | Description & System Design Context |
| :--- | :--- | :--- | :--- |
| `INCR key` / `DECR key` | $O(1)$ | Atomic | Increments/decrements integer by 1. Auto-initializes non-existent key to `0` before operating. |
| `INCRBY key increment` / `DECRBY key decrement` | $O(1)$ | Atomic | Increments/decrements by signed 64-bit integer. |
| `INCRBYFLOAT key increment` | $O(1)$ | Atomic | Increments by double-precision IEEE 754 floating-point number. |

> [!WARNING]
> **Edge Cases**:
> - Executing `INCR` on a non-numeric string throws `ERR value is not an integer or out of range`.
> - If incrementing exceeds signed 64-bit maximum ($9,223,372,036,854,775,807$), Redis throws `ERR increment or decrement would overflow`.

### D. Substring & Offset Operations

| Command & Syntax | Complexity | Atomicity | Description |
| :--- | :--- | :--- | :--- |
| `APPEND key value` | $O(1)$ amortized | Atomic | Appends string to end of existing value. Returns new byte length. |
| `STRLEN key` | $O(1)$ | Atomic | Returns byte length of string (0 if key does not exist). |
| `SETRANGE key offset value` | $O(M)$ where $M$ is value length | Atomic | Overwrites part of string at offset. If offset > existing length, intermediate holes are padded with `\x00` (zero bytes). |
| `GETRANGE key start end` | $O(N)$ where $N$ is range length | Atomic | Returns substring between `start` and `end` (inclusive). Supports negative indices (`-1` = last byte). |

> [!CAUTION]
> **UTF-8 Multi-byte Trap:**
> `STRLEN`, `SETRANGE`, and `GETRANGE` operate on **bytes**, not Unicode characters! A 4-byte emoji (e.g. `🚀`) will report `STRLEN` as `4`. Slicing mid-character via `GETRANGE` produces corrupted byte sequences.

### E. Bitmaps (Bit-level Operations on Strings)

| Command & Syntax | Complexity | Atomicity | Description |
| :--- | :--- | :--- | :--- |
| `SETBIT key offset value` | $O(1)$ | Atomic | Sets or clears bit at offset (0 or 1). Returns previous bit. |
| `GETBIT key offset` | $O(1)$ | Atomic | Returns bit at offset (returns 0 if offset is beyond current allocation). |
| `BITCOUNT key [start end [BYTE\|BIT]]` | $O(N)$ | Atomic | Population count (Hamming weight). Counts set bits using CPU SIMD vector instructions. |
| `BITPOS key bit [start [end]]` | $O(N)$ | Atomic | Returns first bit set to 0 or 1. |
| `BITOP AND\|OR\|XOR\|NOT dest src [src ...]` | $O(N)$ | Atomic | Bitwise boolean operations across one or more bit arrays. |

---

## 4. SDE-2 System Design Patterns & Blueprints

### Pattern 1: Distributed Mutual Exclusion Lock (Redlock Primitive)

#### The Problem:
Multiple distributed worker pods must process an order or deduct inventory concurrently without race conditions.

#### The Flawed Anti-Pattern:
```ts
// ❌ DANGEROUS ANTI-PATTERN: DO NOT USE IN PRODUCTION
const acquired = await redis.setnx("lock:order:101", "worker-1");
if (acquired) {
  await redis.expire("lock:order:101", 10); // IF POD CRASHES HERE, DEADLOCK!
}
```

#### Production Solution:
Use single-command atomic `SET ... NX PX` with a cryptographically unique worker token:

```ts
// 1. Atomic Acquisition (O(1))
const lockToken = crypto.randomUUID();
const acquired = await redis.set("lock:order:101", lockToken, {
  NX: true,
  PX: 5000, // 5 second lease
});

if (!acquired) {
  throw new Error("Resource is locked by another worker");
}

try {
  // Execute critical section
  await processPayment();
} finally {
  // 2. Safe Atomic Release via Lua Script
  // Prevents releasing another worker's lock if TTL expired during slow execution
  const unlockLua = `
    if redis.call("get", KEYS[1]) == ARGV[1] then
      return redis.call("del", KEYS[1])
    else
      return 0
    end
  `;
  await redis.eval(unlockLua, { keys: ["lock:order:101"], arguments: [lockToken] });
}
```

---

### Pattern 2: Atomic Fixed-Window Rate Limiter

#### The Problem:
Limit API access to 100 requests per minute per user ID without race conditions on counter initialization and expiration.

#### Production Solution:
Use an atomic Lua script combining `INCR` and conditional `EXPIRE`:

```lua
local current = redis.call("INCR", KEYS[1])
if current == 1 then
  redis.call("EXPIRE", KEYS[1], ARGV[1])
end
local ttl = redis.call("PTTL", KEYS[1])
return { current, ttl }
```

---

### Pattern 3: Large-Scale User Analytics with Bitmaps

#### System Design Scenario:
An analytics platform tracks **100,000,000 Daily Active Users (DAU)** and must compute **7-day retention** in real time.

#### Memory Comparison:
- **Set of UUID strings:** $100,000,000 \times 36\text{ bytes} \approx 3.6\text{ GB RAM}$
- **Set of 64-bit user integers:** $100,000,000 \times 8\text{ bytes} + \text{Set pointers} \approx 1.2\text{ GB RAM}$
- **Redis Bitmap (1 bit per integer User ID):**
  $$\frac{100,000,000\text{ bits}}{8\text{ bits/byte} \times 1024 \times 1024} \approx \mathbf{11.92\text{ MB RAM!}}$$

#### Computing Retention with Single-Command Bitwise Intersection:
```ts
// Day 1: User 42 logs in
await redis.setBit("dau:2026-11-09", 42, 1);

// Day 2: User 42 logs in
await redis.setBit("dau:2026-11-10", 42, 1);

// Compute users active on BOTH days (Intersection)
await redis.bitOp("AND", "dau:retained:mon-tue", ["dau:2026-11-09", "dau:2026-11-10"]);

// Count retained users in sub-millisecond CPU time
const retainedUserCount = await redis.bitCount("dau:retained:mon-tue");
```

> [!CAUTION]
> **Sparse Allocation Warning:**
> If a user ID is `2,000,000,000`, running `SETBIT user:bitmap 2000000000 1` forces Redis to allocate intermediate bytes up to that offset ($\approx 238\text{ MB}$) in a single allocation! Always map non-contiguous user IDs to sequential integers or use Redis HyperLogLog / Bloom Filters for sparse spaces.

---

## 5. Performance Benchmark Insights

From our live benchmark suite (`npm run bench:strings`) running 1,000 items:

```
=========================================================================================================
| Operation / Strategy         |     Ops |  Time(ms) |     Ops/sec |  Avg(ms) |  p50(ms) |  p99(ms) |
---------------------------------------------------------------------------------------------------------
| 1. Sequential SET            |    1000 |     634.1 |      1576.9 |    0.633 |    0.597 |    1.080 |
| 2. Pipelined SET (chunk 100) |    1000 |      15.4 |     65018.7 |    0.015 |    0.015 |    0.027 |
| 3. Multi-Key MSET (chunk 100) |    1000 |      10.0 |    100179.3 |    0.010 |    0.010 |    0.013 |
| 4. Sequential GET            |    1000 |     695.0 |      1439.0 |    0.694 |    0.652 |    1.125 |
| 5. Pipelined GET (chunk 100) |    1000 |      11.4 |     87773.2 |    0.011 |    0.012 |    0.014 |
| 6. Multi-Key MGET (chunk 100) |    1000 |       9.9 |    101222.8 |    0.010 |    0.009 |    0.019 |
=========================================================================================================
```

### Key Takeaways for System Design:
1. **Network Round-Trip Time (RTT) is the Dominant Bottleneck:**
   Sequential operations are bounded by network latency ($\sim 1,500$ ops/sec with $0.6\text{ms}$ RTT).
2. **Pipelining Yields $40\times - 60\times$ Speedup:**
   By amortizing TCP packets and system calls, pipelining increases throughput to $>65,000$ ops/sec.
3. **`MSET`/`MGET` vs Pipelining:**
   `MSET` is slightly faster than pipelined `SET` because Redis processes a single command parsing overhead, but `MSET` is executed as one contiguous atomic block. In Redis Cluster, multi-key operations require all keys to hash to the exact same hash slot (`{hash_tag}`). Pipelining with individual keys is cluster-agnostic and avoids cross-slot limitations!

---

## 6. How to Run This Module

```bash
# 1. Run Vitest Unit & Integration Test Suite
npm run test:strings

# 2. Run Benchmarks
npm run bench:strings

# 3. Run Standalone Experiments
npm run exp:encodings   # SDS memory encodings
npm run exp:locks       # Distributed locking & safe Lua release
npm run exp:bitmaps     # 100M user DAU analytics with Bitmaps
```

