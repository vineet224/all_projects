# Redis Sets Module (SDE-2 System Design Deep Dive)

A Redis Set is an **unordered collection of unique, binary-safe strings**. It provides constant-time $O(1)$ membership checks, additions, and removals, alongside server-side set algebra (unions, intersections, differences).

---

## 1. Architectural Internals & Encodings

Redis Sets use two primary encodings:

| Encoding | Criteria | Data Structure | Lookup Complexity | Memory Efficiency |
| :--- | :--- | :--- | :--- | :--- |
| **`intset`** | All members are signed integers (16, 32, or 64-bit) **AND** count $\le 512$ (`set-max-intset-entries`) | Sorted contiguous array of integers with binary search | $O(\log N)$ | **Extremely dense**. No hash tables, no pointers, dynamic width upgrade |
| **`hashtable`** (or `listpack` in 7.2+) | Any non-integer member **OR** count $> 512$ | Hash dictionary with null values (keys only) | $O(1)$ amortized | Standard dict overhead |

---

## 2. Command Reference Matrix

| Command & Syntax | Complexity | Atomicity | Since | Description & Edge Cases |
| :--- | :--- | :--- | :--- | :--- |
| `SADD key member [member ...]` | $O(1)$ per member | Atomic | 1.0.0 | Adds member(s). Duplicates are ignored. Returns count of newly added members. |
| `SREM key member [member ...]` | $O(1)$ per member | Atomic | 1.0.0 | Removes member(s). Returns count of removed members. |
| `SISMEMBER key member` | $O(1)$ | Atomic | 1.0.0 | Returns 1 if member exists, 0 otherwise. |
| `SMISMEMBER key member [member ...]` | $O(N)$ | Atomic | **6.2.0** | Batch membership check. Returns array of booleans. |
| `SCARD key` | $O(1)$ | Atomic | 1.0.0 | Returns set cardinality (0 if key missing). |
| `SMEMBERS key` | $O(N)$ | Atomic | 1.0.0 | Returns all members. **⚠️ Anti-pattern** for large sets; use `SSCAN`. |
| `SPOP key [count]` | $O(1)$ | Atomic | 1.0.0 | Removes and returns random member(s) (*sampling without replacement*). |
| `SRANDMEMBER key [count]` | $O(1)$ or $O(N)$ | Atomic | 1.0.0 | Returns random member(s) without removing (*sampling with replacement*). |
| `SINTER key [key ...]` | $O(N \times M)$ | Atomic | 1.0.0 | Intersects multiple sets. $N$ = size of smallest set. |
| `SINTERCARD numkeys key [key ...] [LIMIT limit]` | $O(N \times M)$ | Atomic | **7.0.0** | Returns intersection cardinality without transmitting elements. |
| `SUNION key [key ...]` | $O(N)$ | Atomic | 1.0.0 | Union of multiple sets. $N$ = total elements across all sets. |
| `SDIFF key [key ...]` | $O(N)$ | Atomic | 1.0.0 | Difference between first set and subsequent sets ($A - B - C$). |
| `SINTERSTORE / SUNIONSTORE / SDIFFSTORE` | $O(N)$ | Atomic | 1.0.0 | Computes set algebra and writes result directly to a destination key. |
| `SSCAN key cursor [MATCH pat] [COUNT n]` | $O(1)$ per call | Atomic | 2.8.0 | Non-blocking cursor-based iteration. Safe for production. |

---

## 3. SDE-2 System Design Patterns

### Pattern 1: Social Graph & Relationship Engine
- **Followers & Following:** Model `user:101:following` and `user:101:followers`.
- **Mutual Friends:** `SINTER user:A:following user:B:following` computes shared connections in C memory. Redis optimizes this by sorting input sets by cardinality and iterating through the smallest set!
- **Friend Recommendations:** `SDIFF user:B:following user:A:following` discovers contacts of B not yet followed by A.
- **UI Optimization:** Use `SINTERCARD` to show "You have 15 mutual friends" without transferring 15 user IDs over the network.

### Pattern 2: Fair Random Giveaway / Lottery
- Populate participant IDs: `SADD giveaway:101 user_1 user_2 ...`
- Pick 5 winners without duplicate selection: `SPOP giveaway:101 5` (atomic removal ensures no two pods can pick the same winner).

---

## 4. How to Run This Module

```bash
# Run Vitest tests for Sets
npm run test:sets

# Run standalone social graph experiment
npm run exp:sets:social-graph
```

