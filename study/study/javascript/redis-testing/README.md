# redis-operations-lab

A hands-on, production-grade Redis operations lab designed for backend **SDE-2 system-design interviews**.

Connects directly to an actual local Redis instance (using Docker Compose and the official Redis Node.js client) to test, verify, and deeply understand Redis command semantics, time complexity, atomicity guarantees, internal memory encodings, and distributed architecture patterns.

---

## 🏗️ Modules Architecture

```
redis-operations-lab/
├── docker-compose.yml              # Official Redis 8 container definition & healthcheck
├── vitest.config.ts                # Sequential test runner configuration
├── src/
│   ├── client/
│   │   ├── redis-client.ts         # Official Redis client manager & connection pooling
│   │   ├── key-manager.ts          # Isolated test keyspace (`lab:<module>:<runId>:*`) & non-blocking SCAN cleanup
│   │   └── index.ts
│   └── modules/
│       ├── strings/                # Complete Redis Strings & Bitmaps Lab
│       │   ├── README.md           # SDE-2 deep-dive: SDS internals, encodings, big-O, system-design blueprints
│       │   ├── strings.service.ts  # String commands, bitwise ops & Lua scripts
│       │   ├── strings.test.ts     # 29 Vitest tests (overflows, TTL, bitmaps, locks, rate limiter)
│       │   └── experiments/        # Standalone runnable SDE-2 experiments
│       │       ├── 01-encodings.ts # SDS INT, EMBSTR, RAW memory layout demonstration
│       │       ├── 02-atomic-locks.ts # Distributed locks & safe atomic Lua unlock
│       │       └── 03-bitmaps-analytics.ts # 100M user DAU & retention analysis with Bitmaps
│       │
│       ├── hashes/                 # Complete Redis Hashes Lab
│       │   ├── README.md           # listpack vs hashtable encodings, field-level TTL, HGETALL anti-pattern
│       │   ├── hashes.service.ts   # Hash commands, HSCAN, and Redis 7.4+ HEXPIRE/HTTL
│       │   ├── hashes.test.ts      # 13 Vitest tests (field expiration, inspection, cart & profile patterns)
│       │   └── experiments/
│       │       ├── 01-hash-encodings.ts # listpack to hashtable transition threshold
│       │       ├── 02-json-vs-hash.ts   # System design showdown: JSON String vs Redis Hash
│       │       └── 03-field-expiration.ts # Redis 7.4+ field-level TTL (HEXPIRE)
│       │
│       ├── sets/                   # Complete Redis Sets Lab
│       │   ├── README.md           # intset vs hashtable encodings, set algebra complexity
│       │   ├── sets.service.ts     # Set commands, SINTER, SUNION, SDIFF, SINTERCARD
│       │   ├── sets.test.ts        # 11 Vitest tests (uniqueness, random sampling, social graph)
│       │   └── experiments/
│       │       └── 01-social-graph.ts  # Mutual connections & recommendation engine
│       │
│       ├── sorted-sets/            # Complete Redis Sorted Sets (ZSet) Lab
│       │   ├── README.md           # SkipList + Dict architecture, why SkipLists over Red-Black trees
│       │   ├── sorted-sets.service.ts # ZSet operations, range queries, sliding window limiter
│       │   ├── sorted-sets.test.ts # 11 Vitest tests (tie-breaking, GT flags, leaderboard, rate limiter)
│       │   └── experiments/
│       │       ├── 01-leaderboard.ts   # Real-time gaming leaderboard with rank lookup
│       │       └── 02-sliding-window-rate-limiter.ts # Perfect sliding window log rate limiter
│       │
│       ├── lists/                  # Complete Redis Lists Lab
│       │   ├── README.md           # quicklist & listpack hybrid architecture, FIFO/LIFO queues
│       │   ├── lists.service.ts    # List operations, atomic LMOVE, timeline fan-out
│       │   ├── lists.test.ts       # 12 Vitest tests (bounded feeds, reliable task claiming)
│       │   └── experiments/
│       │       ├── 01-chronological-feed.ts # Twitter/Instagram timeline feed (LPUSH + LTRIM)
│       │       └── 02-reliable-queue.ts     # At-least-once reliable queue with LMOVE
│       │
│       └── advanced-concepts/      # Master Architectural Guide for Remaining Topics
│           └── README.md           # Streams, Transactions, Pipelining, Generic Keys, Persistence, Replication, Sentinel, Cluster, Eviction
```

---

## 🚀 Quickstart Commands

### 1. Run All Vitest Test Suites
```bash
# Run all 76 integration tests across Strings, Hashes, Sets, Sorted Sets, and Lists
npm test
```

Or run module test suites individually:
```bash
npm run test:strings       # Strings & Bitmaps (29 tests)
npm run test:hashes        # Hashes & Field Expiration (13 tests)
npm run test:sets          # Sets & Set Algebra (11 tests)
npm run test:sorted-sets   # Sorted Sets & Leaderboards (11 tests)
npm run test:lists         # Lists & Queues (12 tests)
```

---

### 2. Run Interactive SDE-2 Experiments

```bash
# Strings
npm run exp:strings:encodings      # SDS memory encodings (int, embstr, raw)
npm run exp:strings:locks          # Production distributed mutual exclusion lock
npm run exp:strings:bitmaps        # 100M user DAU & retention analysis with Bitmaps

# Hashes
npm run exp:hashes:encodings       # listpack to hashtable transition threshold
npm run exp:hashes:json-vs-hash    # JSON String vs Redis Hash architectural comparison
npm run exp:hashes:field-expiry    # Redis 7.4+ field-level TTL (HEXPIRE)

# Sets
npm run exp:sets:social-graph      # Mutual connections & recommendation engine

# Sorted Sets
npm run exp:sorted-sets:leaderboard    # Real-time gaming leaderboard with rank lookup
npm run exp:sorted-sets:rate-limiter   # Sliding window log rate limiter

# Lists
npm run exp:lists:feed             # Chronological timeline feed (LPUSH + LTRIM)
npm run exp:lists:queue            # Reliable worker task queue with atomic LMOVE
```

---

## 📚 Module Documentation Links
- [Redis Strings Deep Dive](file:///c:/vineet_personal/all_projects/study/study/javascript/redis-testing/src/modules/strings/README.md)
- [Redis Hashes Deep Dive](file:///c:/vineet_personal/all_projects/study/study/javascript/redis-testing/src/modules/hashes/README.md)
- [Redis Sets Deep Dive](file:///c:/vineet_personal/all_projects/study/study/javascript/redis-testing/src/modules/sets/README.md)
- [Redis Sorted Sets Deep Dive](file:///c:/vineet_personal/all_projects/study/study/javascript/redis-testing/src/modules/sorted-sets/README.md)
- [Redis Lists Deep Dive](file:///c:/vineet_personal/all_projects/study/study/javascript/redis-testing/src/modules/lists/README.md)
- [Advanced Architecture Guide (Streams, Transactions, Pipelining, Sentinel, Cluster, Eviction)](file:///c:/vineet_personal/all_projects/study/study/javascript/redis-testing/src/modules/advanced-concepts/README.md)
