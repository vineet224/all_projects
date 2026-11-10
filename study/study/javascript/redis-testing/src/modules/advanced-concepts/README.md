# Advanced Redis Concepts: Systems Design & Architecture Guide

A comprehensive architectural reference covering **Streams, Transactions, Pipelining, Generic Key Operations, Persistence, Replication, Sentinel, Cluster, and Memory Eviction** for backend SDE-2 system-design interviews.

---

# Table of Contents
1. [Redis Streams & Message Brokers](#1-redis-streams--message-brokers)
2. [Transactions & Concurrency Control (MULTI/EXEC/WATCH)](#2-transactions--concurrency-control-multiexecwatch)
3. [Pipelining: Network Amortization](#3-pipelining-network-amortization)
4. [Generic Key Operations & Background Mechanics](#4-generic-key-operations--background-mechanics)
5. [Persistence: RDB, AOF & Hybrid](#5-persistence-rdb-aof--hybrid)
6. [Replication, Sentinel & High Availability](#6-replication-sentinel--high-availability)
7. [Redis Cluster: Distributed Sharding & 16,384 Slots](#7-redis-cluster-distributed-sharding--16384-slots)
8. [Memory Management & Eviction Policies](#8-memory-management--eviction-policies)

---

# 1. Redis Streams & Message Brokers

Introduced in Redis 5.0, a **Stream** is an append-only log data structure modeled after Apache Kafka, designed for high-throughput event sourcing, activity feeds, and decoupled worker queues.

### Internal Data Structure: Radix Tree of Listpacks
Under the hood, a Redis Stream is implemented as a **Radix Tree (Rax)** where each node contains a contiguous **`listpack`** holding multiple stream entries. This hybrid architecture provides:
- Compact memory footprint (tens of stream messages compressed into a single listpack).
- $O(1)$ append performance at the tail.
- $O(\log N)$ random seeks by timestamp or ID.

### Message Identifiers
Stream IDs are strictly monotonically increasing 64-bit millisecond timestamps and sequence counters:
$$\text{ID} = \langle\text{millisecondsTime}\rangle-\langle\text{sequenceNumber}\rangle \quad (\text{e.g. } 1718042948123-0)$$
Passing `*` to `XADD` automatically generates an ID using the server's local high-resolution clock.

### Core Stream Commands

| Command & Syntax | Complexity | Description |
| :--- | :--- | :--- |
| `XADD key [NOMKSTREAM] [MAXLEN ~ n] ID field value ...` | $O(1)$ | Appends message to stream. `MAXLEN ~ n` trims stream approximately with near-zero CPU overhead. |
| `XREAD [COUNT n] [BLOCK ms] STREAMS key ... ID ...` | $O(N)$ | Reads messages with IDs greater than specified ID. Supports blocking reads (`BLOCK 5000`). |
| `XRANGE key start end [COUNT n]` | $O(\log N + M)$ | Queries range of messages between IDs (use `-` for minimum, `+` for maximum). |
| `XLEN key` | $O(1)$ | Returns total number of entries in the stream. |
| `XTRIM key MAXLEN\|MINID [~] threshold` | $O(N)$ | Explicitly prunes older messages by length or minimum timestamp. |

---

### Consumer Groups Architecture
Consumer groups allow multiple workers to cooperatively consume disjoint subsets of messages from the same stream:

```
                      +-------------------+
                      |   Redis Stream    |
                      |   (Append-Only)   |
                      +-------------------+
                                |
             +------------------+------------------+
             v                                     v
   +-------------------+                 +-------------------+
   | Consumer Group A  |                 | Consumer Group B  |
   | (Audit Service)   |                 | (Email Workers)   |
   +-------------------+                 +-------------------+
      /             \                       /             \
  Worker-1       Worker-2               Worker-1       Worker-2
```

1. **`XGROUP CREATE key groupname ID [MKSTREAM]`**: Initializes a group starting from an ID (`0` = beginning, `$` = newest messages only).
2. **`XREADGROUP GROUP group consumer [COUNT n] [BLOCK ms] STREAMS key >`**:
   - `>`: Fetches messages that have **never been delivered to any consumer** in this group.
   - Any message read enters the group's **Pending Entries List (PEL)**.
3. **`XACK key group ID ...`**: Consumer acknowledges successful processing, removing message from PEL.
4. **Crash Recovery & Dead-Letter Handling**:
   - **`XPENDING key group`**: Inspects stuck messages delivered to dead/slow workers.
   - **`XCLAIM` / `XAUTOCLAIM`**: Reassigns abandoned in-flight messages to healthy workers after idle threshold.

### SDE-2 Comparison: Redis Streams vs Kafka vs RabbitMQ vs Pub/Sub

| Feature | Redis Streams | Apache Kafka | RabbitMQ | Redis Pub/Sub |
| :--- | :--- | :--- | :--- | :--- |
| **Storage Model** | In-memory log (RDB/AOF backed) | Disk-persisted commit log | Erlang queue in RAM/Disk | Ephemeral (zero persistence) |
| **Message History** | Preserved up to `MAXLEN` | Preserved by retention period | Deleted upon consumer ACK | **Dropped immediately if no listener** |
| **Consumer Groups** | Supported with PEL & ACK | Partition-based consumer groups | Competing consumers pattern | Not supported (fan-out only) |
| **Throughput** | $500\text{K}+ \text{ msg/sec}$ (sub-ms) | Millions msg/sec (batched disk) | $\sim 50\text{K} \text{ msg/sec}$ | $1\text{M}+ \text{ msg/sec}$ |
| **Best Used For** | Fast event pipelines, audit trails | Enterprise event backbone | Complex routing (fanout/topic/direct) | Live chat, UI notifications |

---

# 2. Transactions & Concurrency Control (MULTI/EXEC/WATCH)

Redis transactions group multiple commands into a single sequential execution unit.

### Core Commands
- **`MULTI`**: Enters transaction mode. Subsequent commands are serialized in a FIFO command queue and return `QUEUED`.
- **`EXEC`**: Atomically executes all queued commands in a single uninterruptible pass.
- **`DISCARD`**: Aborts transaction queue and flushes pending commands.
- **`WATCH key [key ...]`**: Optimistic locking primitive. Monitors keys for external changes.

---

### Optimistic Concurrency Control (CAS Pattern with WATCH)
```ts
await client.watch("inventory:sku_901");
const stock = parseInt(await client.get("inventory:sku_901") || "0", 10);

if (stock > 0) {
  const multi = client.multi();
  multi.decr("inventory:sku_901");
  multi.incr("metrics:total_sales");
  const results = await multi.exec();

  if (results === null) {
    // WATCH detected an external write before EXEC! Transaction was aborted.
    throw new Error("Conflict detected. Please retry purchase.");
  }
} else {
  await client.unwatch();
}
```

---

### The Classic Interview Question: Why Doesn't Redis Support Rollback?

In relational databases (PostgreSQL/MySQL), a transaction failure triggers an automated rollback to restore consistency. **Redis does NOT roll back on runtime failures.**

```redis
MULTI
SET balance 1000
INCR user_name    # FAILS AT RUNTIME: WRONGTYPE (user_name is not an integer)
INCR balance      # STILL SUCCEEDS! balance becomes 1001
EXEC
```

#### Why Redis Chose This Design:
1. **Programming Errors vs System Faults**: In Redis, runtime errors (e.g. calling `INCR` on a non-numeric string) are developer bugs that should be caught in unit tests, not production system faults like disk fullness or unique constraint violations.
2. **Performance**: Maintaining undo logs and transaction rollback buffers would drastically degrade Redis's in-memory performance and single-threaded execution speed.
3. **Simplicity**: Eliminates complex ACID locking mechanisms and deadlocks.

---

### Redis Transactions vs Lua Scripting

| Dimension | `MULTI / EXEC` | Lua Script (`EVAL`) |
| :--- | :--- | :--- |
| **Conditional Logic** | ❌ Cannot branch (`if/else`) based on intermediate command outputs | ✅ Full Turing-complete scripting with conditional branching |
| **Network Round-Trips** | Requires separate `WATCH` $\to$ `GET` $\to$ `MULTI` $\to$ `EXEC` calls | ✅ Single network round-trip |
| **Conflict Handling** | Fails with `null` on `WATCH` conflict (client must retry) | ✅ Never aborts on concurrent writes; executes atomically in-engine |
| **Production Recommendation** | Use for simple unconditional batch sets | **Preferred standard for distributed systems & atomicity** |

---

# 3. Pipelining: Network Amortization

Redis operates on a client-server model using the Request/Response RESP protocol. In standard synchronous calls:
$$\text{Total Time} = N \times (\text{Network RTT} + \text{Server Execution Time})$$

When client and server are separated by a $1\text{ms}$ network ping, sequential execution of 1,000 commands takes **at least 1 second**, even if Redis processes each command in 5 microseconds!

### How Pipelining Works
Pipelining allows the client to write multiple commands to the TCP socket buffer without waiting for intermediate replies. Redis reads all commands from the socket, executes them sequentially, and transmits replies packed into contiguous TCP segments:

```
Client                                                  Redis Server
  |                                                           |
  |--- SET key1 "val1" -------------------------------------->|
  |--- SET key2 "val2" -------------------------------------->|
  |--- SET key3 "val3" -------------------------------------->|
  |                                                           |
  |                                                     [Executes all 3]
  |                                                           |
  |<-- [OK, OK, OK] (Single TCP Packet Reply) ----------------|
```

### Pipelining vs Transactions vs Multi-Key Commands

| Mechanism | Atomicity | Interleaving Guarantee | Cluster Cross-Slot Constraint |
| :--- | :--- | :--- | :--- |
| **Pipelining** | ❌ Not atomic | Other clients' commands **can interleave** between pipelined commands | ✅ Works across different cluster nodes (client batches by node) |
| **`MULTI / EXEC`** | ✅ Atomic | **Zero interleaving** (uninterruptible block) | ❌ All keys must reside on the **same hash slot** |
| **`MSET / MGET`** | ✅ Atomic | Single command | ❌ All keys must reside on the **same hash slot** (`{hash_tag}`) |

---

# 4. Generic Key Operations & Background Mechanics

Generic commands operate across keys regardless of their underlying data structure.

### `DEL` vs `UNLINK` (Synchronous Blocking vs Async Background Reclamation)

- **`DEL key [key ...]`**: Synchronously removes key and immediately frees memory in the main thread.
  - ⚠️ If deleting a set or hash with 2,000,000 elements, `DEL` blocks the entire Redis single thread for seconds!
- **`UNLINK key [key ...]`**: Removes key from the keyspace dictionary in $O(1)$ time, and delegates actual memory deallocation to a background worker thread (`bio.c` - Background I/O thread).
  - ✅ **SDE-2 Best Practice:** Always use `UNLINK` in production systems.

### Expiration Algorithms: Passive vs Active
How does Redis evict expired keys without scanning millions of entries?

1. **Passive Eviction (Lazy)**: When a client accesses a key (e.g. `GET key`), Redis checks if its TTL has passed. If expired, it deletes the key and returns `null`.
2. **Active Eviction (Periodic Background Sweeper)**:
   - 10 times per second (`hz = 10`), Redis executes an active test:
     1. Randomly sample 20 keys with an associated TTL.
     2. Delete all keys found expired.
     3. If more than 25% of the sampled keys were expired, repeat immediately!
   - This probabilistic sampling prevents memory from being clogged by keys that are never read again.

### Production Danger: `KEYS *` vs `SCAN`
- `KEYS *`: Scans entire keyspace in $O(N)$ time. In production with 10M keys, this causes complete server lockup and cascades outage across microservices.
- `SCAN cursor [MATCH pat] [COUNT n]`: Cursor-based state-less iterator. Returns a small batch of keys and a new cursor (`0` = finished), guaranteeing zero server lockups.

---

# 5. Persistence: RDB, AOF & Hybrid

Redis maintains in-memory data, but provides durability through two complementary persistence engines:

```
                                  +-------------------+
                                  | Redis In-Memory DB|
                                  +-------------------+
                                    /               \
                                   /                 \
                                  v                   v
                        +------------------+  +------------------+
                        |  RDB (Snapshot)  |  |  AOF (Log)       |
                        |  Point-in-time   |  |  Append-only     |
                        |  Binary Dump     |  |  Command Log     |
                        +------------------+  +------------------+
```

### 1. RDB (Redis Database Snapshot)
- Creates a compact point-in-time binary snapshot (`dump.rdb`) at configured intervals (e.g. `save 300 10`).
- **Mechanism:** Redis invokes `fork()` to create a child process. The child writes to disk using the operating system's **Copy-on-Write (COW)** virtual memory paging.
- **Pros:**
  - Fast server recovery (restoring 10GB RDB is $10\times$ faster than replaying AOF logs).
  - Zero main-thread disk I/O overhead during snapshotting.
- **Cons:**
  - High data loss window (if server crashes, all writes since last snapshot are lost).
  - Heavy memory overhead: During heavy write traffic, Copy-on-Write can duplicate up to $100\%$ of Redis memory!

### 2. AOF (Append-Only File)
- Logs every write command received by Redis in the RESP format to disk.
- **`fsync` Policies:**
  - **`appendfsync always`**: Fsyncs after every command. Maximum durability, severe disk bottleneck ($\approx 3,000$ ops/s).
  - **`appendfsync everysec`** (Default): Fsyncs once every second on a background thread. Balance of performance and durability (at most 1-2 seconds of data loss).
  - **`appendfsync no`**: Delegates fsync to OS kernel buffers. Maximum speed, indeterminate durability.
- **AOF Rewrite (`BGREWRITEAOF`)**: When AOF file grows too large, Redis forks a child process to regenerate an optimized AOF log reflecting only current keyspace state.

### 3. Modern Hybrid Persistence (Redis 4.0+)
Combines RDB snapshot speed with AOF incremental durability. The rewritten file contains an RDB binary preamble followed by an incremental AOF tail.

---

# 6. Replication, Sentinel & High Availability

To eliminate single points of failure, Redis implements asynchronous Primary-Replica replication and automated Sentinel failover.

### Replication Internals
1. **Initial Full Synchronization**:
   - Replica connects and sends `PSYNC ? -1`.
   - Primary starts background `bgsave`, buffers incoming writes in **Replication Backlog Buffer**, and streams RDB to replica.
   - Replica flushes old data and loads RDB into memory.
2. **Partial Resynchronization (`PSYNC`)**:
   - After brief network disconnects, replica reconnects with Primary Run ID and byte offset.
   - If offset is still in Primary's circular backlog buffer (`repl-backlog-size`), Primary sends only missing delta bytes without full RDB re-dump.

### Split-Brain & Asynchronous Data Loss Mitigation
Because replication is asynchronous, a network partition can create a split-brain condition where both old primary and new primary accept writes.
Mitigate using:
```conf
min-replicas-to-write 1
min-replicas-max-lag 10
```
*(Primary rejects writes if fewer than 1 replica acknowledges within 10 seconds).*

---

### Redis Sentinel Architecture
Sentinel is an external monitoring cluster running in quorum:

```
                   +------------------+
                   |  Sentinel Node 1 |
                   +------------------+
                     /      |       \
                    /       |        \
   +------------------+     |     +------------------+
   |  Sentinel Node 2 |<----+---->|  Sentinel Node 3 |
   +------------------+           +------------------+
            |                              |
            v                              v
      +-----------+                 +-----------+
      |  PRIMARY  | ===(Repl)===>   |  REPLICA  |
      +-----------+                 +-----------+
```

1. **Monitoring**: Sentinels send continuous `PING` commands to Primary and Replicas.
2. **Subjective Down (`sdown`)**: A single Sentinel detects a node is unreachable for `down-after-milliseconds`.
3. **Objective Down (`odown`)**: A quorum of Sentinels agree that Primary is down.
4. **Leader Election & Failover**: Sentinels elect a Raft-style leader among themselves to promote the replica with highest priority, offset, and lowest run ID to become the new Primary.

---

# 7. Redis Cluster: Distributed Sharding & 16,384 Slots

When data size exceeds single-node RAM or throughput exceeds single-node network capacity, **Redis Cluster** provides horizontal partitioning and linear scalability.

### The 16,384 Hash Slot Topology
Redis Cluster does NOT use consistent hashing rings. Instead, the keyspace is divided into **16,384 static Hash Slots**:

$$\text{Slot} = \text{CRC16}(\text{key}) \pmod{16384}$$

Slots are partitioned across master nodes:
- Master 1: Slots `0` to `5460`
- Master 2: Slots `5461` to `10922`
- Master 3: Slots `10923` to `16383`

### Hash Tags (`{...}` Notation)
By default, multi-key operations (`MSET`, `MGET`, `SUNION`, Lua scripts) fail with `CROSSSLOT` error if keys belong to different slots.
Use **Hash Tags** to force keys to map to the exact same hash slot:
```redis
SET {user:101}:profile "Alice"      # CRC16 hashes ONLY the string inside {} -> "user:101"
SET {user:101}:orders  "[1, 2, 3]"   # Hashes to the SAME slot!
MGET {user:101}:profile {user:101}:orders  # ✅ SUCCEEDS: Same slot!
```

### Client Redirection: `MOVED` vs `ASK`
1. **`MOVED <slot> <ip:port>`**: Permanent redirect. Client updates its internal slot-to-node routing table.
2. **`ASK <slot> <ip:port>`**: Temporary redirect during active slot migration / resharding. Client sends `ASKING` command before retrying on target node.

---

# 8. Memory Management & Eviction Policies

When Redis reaches its allocated RAM limit (`maxmemory`), it applies an **eviction policy** to reclaim memory.

### The 8 Eviction Policies

| Eviction Policy | Target Keys | Eviction Criteria | System Design Scenario |
| :--- | :--- | :--- | :--- |
| **`noeviction`** (Default) | None | **Throws OOM error** on any new write (`OOM command not allowed when used memory > 'maxmemory'`). | Critical transactional systems where data loss is intolerable. |
| **`allkeys-lru`** | All keys | Least Recently Used (LRU) across entire keyspace. | General-purpose web cache with power-law access patterns. |
| **`volatile-lru`** | Keys with TTL | Least Recently Used among expiring keys. | Cache mixed with permanent database keys. |
| **`allkeys-lfu`** | All keys | Least Frequently Used (LFU) access counter. | Prevents popular long-term items from being evicted by sudden scan spikes. |
| **`volatile-lfu`** | Keys with TTL | Least Frequently Used among expiring keys. | Content recommendation and trending feeds. |
| **`allkeys-random`** | All keys | Random eviction. | Uniformly distributed random access workloads. |
| **`volatile-random`** | Keys with TTL | Random eviction among expiring keys. | Cyclic TTL caches. |
| **`volatile-ttl`** | Keys with TTL | Smallest remaining TTL (evicts keys expiring soonest). | Proactive TTL-ordered pruning. |

---

### Approximate LRU vs True LRU
Maintaining a true Doubly-Linked List across millions of keys requires 16-24 bytes of pointer overhead per key and locks list pointers on every read operation.

Redis implements **Approximate LRU**:
1. Every `redisObject` stores a 24-bit timestamp clock (`lru`).
2. When eviction triggers, Redis samples **$N$ random keys** (default $N = 5$, configurable via `maxmemory-samples`).
3. It evicts the key with the oldest idle time from the sample.
4. Setting $N = 10$ produces an eviction curve virtually indistinguishable from mathematical True LRU with zero pointer memory overhead!

