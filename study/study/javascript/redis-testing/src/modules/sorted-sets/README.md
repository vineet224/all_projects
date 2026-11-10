# Redis Sorted Sets Module (SDE-2 System Design Deep Dive)

A Redis Sorted Set (ZSet) is an ordered collection of unique string members, where each member is associated with a floating-point **score**. Members are maintained in sorted order by score (ascending), with ties broken deterministically using **lexicographical string ordering**.

---

## 1. Architectural Internals: Why SkipList + Hash Table?

In C source code (`server.h`), a Redis Sorted Set is implemented using a dual data structure:

```c
typedef struct zset {
    dict *dict;        /* Hash Table: Member -> Score (O(1) lookups) */
    zskiplist *zsl;    /* SkipList: Score -> Member (O(log N) range & rank queries) */
} zset;
```

### The SDE-2 Interview Favorite: Why SkipList Instead of Red-Black Tree or AVL Tree?
1. **Range Queries & Locality**: In a SkipList, nodes at Level 0 form a contiguous singly-linked list. Executing `ZRANGE` or `ZREVRANGE` requires $O(\log N)$ to find the start node, then simple pointer traversal. In balanced binary search trees, range scans require in-order traversals with parent-pointer backtracking.
2. **Rebalancing Overhead**: Red-Black trees require complex rotations that rebalance subtrees. SkipLists rely on probabilistic level assignments (`randomLevel()` with $p = 0.25$), requiring only local pointer adjustments on insert/delete.
3. **Memory Footprint**: By tuning probability $p = 0.25$, each SkipList node averages only 1.33 forward pointers, consuming less memory than parent + left + right pointers + color bits in Red-Black trees.
4. **Lock-Free Concurrency**: SkipLists are easier to adapt into lock-free or fine-grained concurrent structures than balanced trees.

---

## 2. Encodings: `listpack` vs `skiplist`

| Encoding | Threshold Criteria | Lookup Complexity | Range Scan Complexity | Memory Layout |
| :--- | :--- | :--- | :--- | :--- |
| **`listpack`** | Entry count $\le 128$ (`zset-max-listpack-entries`) **AND** member size $\le 64$ bytes | $O(N)$ | $O(N)$ | Compact contiguous binary buffer `[member1, score1, member2, score2, ...]` |
| **`skiplist`** | Entry count $> 128$ **OR** member size $> 64$ bytes | $O(1)$ via dict | $O(\log N + M)$ via zskiplist | Dict pointers + SkipList levels |

---

## 3. Command Reference Matrix

| Command & Syntax | Complexity | Atomicity | Since | Description & System Design Context |
| :--- | :--- | :--- | :--- | :--- |
| `ZADD key [NX\|XX] [GT\|LT] [CH] [INCR] score member ...` | $O(\log N)$ per element | Atomic | 1.2.0 (options 6.2+) | Adds or updates member scores. `GT` only updates if new score > current score. |
| `ZSCORE key member` | $O(1)$ | Atomic | 1.2.0 | Retrieves score using the internal hash table. |
| `ZMSCORE key member [member ...]` | $O(N)$ | Atomic | **6.2.0** | Batch score lookup. Missing members return `null`. |
| `ZINCRBY key increment member` | $O(\log N)$ | Atomic | 1.2.0 | Increments member's score. |
| `ZRANK key member` | $O(\log N)$ | Atomic | 2.0.0 | Returns 0-based rank in ascending order (lowest score = 0). |
| `ZREVRANK key member` | $O(\log N)$ | Atomic | 2.0.0 | Returns 0-based rank in descending order (highest score = 0). |
| `ZRANGE key start stop [WITHSCORES] [REV]` | $O(\log N + M)$ | Atomic | 1.2.0 | Range query by rank. $M$ is number of returned elements. |
| `ZCARD key` | $O(1)$ | Atomic | 1.2.0 | Returns cardinality of sorted set. |
| `ZCOUNT key min max` | $O(\log N)$ | Atomic | 2.0.0 | Counts elements within score range. |
| `ZREM key member [member ...]` | $O(M \times \log N)$ | Atomic | 1.2.0 | Removes member(s). |
| `ZREMRANGEBYSCORE key min max` | $O(\log N + M)$ | Atomic | 1.2.0 | Deletes members by score range. Essential for pruning sliding window logs. |
| `ZPOPMIN / ZPOPMAX key [count]` | $O(M \times \log N)$ | Atomic | 5.0.0 | Pops elements with lowest/highest scores. |

---

## 4. SDE-2 System Design Patterns

### Pattern 1: Real-Time Global Leaderboard
- **Scale:** 10,000,000 players.
- **Score Updates:** `ZINCRBY leaderboard:season1 50 "player_uuid"` ($O(\log N) \approx 24$ operations).
- **Top-100 UI Query:** `ZREVRANGE leaderboard:season1 0 99 WITHSCORES` ($O(\log N + 100) < 1\text{ms}$).
- **Player Rank Lookup:** `ZREVRANK leaderboard:season1 "player_uuid"` ($O(\log N)$).

### Pattern 2: Sliding Window Log Rate Limiter
- Solves the **Boundary Burst Problem** of Fixed-Window counters.
- Stores every request as `{ score: timestamp, member: timestamp:nonce }`.
- Prunes expired timestamps via `ZREMRANGEBYSCORE key -inf (now - window)`.
- Checks remaining quota via `ZCARD`.

---

## 5. How to Run This Module

```bash
# Run Vitest tests for Sorted Sets
npm run test:sorted-sets

# Run standalone experiments
npm run exp:sorted-sets:leaderboard    # Real-time gaming leaderboard
npm run exp:sorted-sets:rate-limiter   # Sliding window log rate limiter
```

