# Redis Lists Module (SDE-2 System Design Deep Dive)

A Redis List is an ordered sequence of binary-safe strings sorted by insertion order. It behaves as a doubly-linked list or `quicklist`, providing constant-time **$O(1)$ insertions and deletions at both ends (head and tail)**.

---

## 1. Architectural Internals: `quicklist` and `listpack`

In Redis source code, Lists are implemented as a **`quicklist`** (a doubly linked list of `listpack` chunks):

```
+-------------+         +-------------+         +-------------+
|  quicklist  | <=====> |  quicklist  | <=====> |  quicklist  |
|    Node     |         |    Node     |         |    Node     |
+-------------+         +-------------+         +-------------+
       |                       |                       |
       v                       v                       v
 [ listpack 1 ]          [ listpack 2 ]          [ listpack 3 ]
 [entry|entry]           [entry|entry]           [entry|entry]
```

### Why `quicklist` instead of Pure Doubly-Linked List or Pure Array?
- **Pure Doubly Linked List:** Incurs 16 bytes of pointer overhead (`prev` + `next`) per element and causes memory fragmentation across the heap.
- **Pure Array:** Reallocating memory on growing lists causes expensive $O(N)$ memory copies and array shifts.
- **`quicklist` Hybrid:** Groups elements into contiguous `listpack` buffers (e.g. 8 KB per chunk). Push/pop at head/tail takes $O(1)$ time, while interior elements benefit from CPU L1/L2 cache prefetching with minimal pointer overhead.
- **Compression (`list-compress-depth`):** Inner nodes of the `quicklist` can be compressed using LZF, leaving only head and tail uncompressed for immediate access.

---

## 2. Command Reference Matrix

| Command & Syntax | Complexity | Atomicity | Since | Description & Edge Cases |
| :--- | :--- | :--- | :--- | :--- |
| `LPUSH key element [element ...]` | $O(1)$ per element | Atomic | 1.0.0 | Prepends element(s) to head. |
| `RPUSH key element [element ...]` | $O(1)$ per element | Atomic | 1.0.0 | Appends element(s) to tail. |
| `LPOP key [count]` | $O(N)$ where $N$ = count | Atomic | 1.0.0 | Pops element(s) from head. Returns `null` on empty list. |
| `RPOP key [count]` | $O(N)$ where $N$ = count | Atomic | 1.0.0 | Pops element(s) from tail. Returns `null` on empty list. |
| `BLPOP / BRPOP key [key ...] timeout` | $O(1)$ | Atomic | 2.0.0 | **Blocking pop**. Waits until element is available or timeout expires (0 = block forever). |
| `LRANGE key start stop` | $O(S + N)$ | Atomic | 1.0.0 | Slices list from `start` to `stop`. Supports negative indices (`-1` = last item). |
| `LTRIM key start stop` | $O(N)$ where $N$ = pruned | Atomic | 1.0.0 | Caps list to range `[start, stop]`. All other elements are discarded. |
| `LLEN key` | $O(1)$ | Atomic | 1.0.0 | Returns total length of list. |
| `LINDEX key index` | $O(N)$ | Atomic | 1.0.0 | Returns element at index ($O(1)$ at head/tail, $O(N)$ in middle). |
| `LSET key index element` | $O(N)$ | Atomic | 1.0.0 | Sets element at index. Throws `ERR index out of range` if invalid. |
| `LREM key count element` | $O(N + M)$ | Atomic | 1.0.0 | Removes occurrences of element. |
| `LMOVE src dest LEFT\|RIGHT LEFT\|RIGHT` | $O(1)$ | Atomic | **6.2.0** | Atomically pops from `src` and pushes to `dest`. Replaces legacy `RPOPLPUSH`. |
| `BLMOVE src dest LEFT\|RIGHT LEFT\|RIGHT timeout` | $O(1)$ | Atomic | **6.2.0** | Blocking atomic list-to-list transfer. |

---

## 3. SDE-2 System Design Patterns

### Pattern 1: Chronological Social Feed (Twitter/Timeline Fan-out)
- When a user posts, prepend post ID to follower feeds: `LPUSH timeline:userId postId`.
- Bound timeline size to prevent unbounded memory growth: `LTRIM timeline:userId 0 799` (keeps 800 most recent items).
- Retrieve paginated feed: `LRANGE timeline:userId 0 19` (first page of 20 items).

### Pattern 2: Reliable Task Processing Queue (At-Least-Once Delivery)
- **The Problem:** A standard `RPOP` loses messages if the worker pod crashes before completing the task.
- **The Solution:** Use atomic `LMOVE`:
  ```ts
  // Worker atomically moves job from pending to processing
  const job = await client.lMove("tasks:pending", "tasks:processing", "RIGHT", "LEFT");
  await executeJob(job);
  // Acknowledge on completion
  await client.lRem("tasks:processing", 1, job);
  ```
- If a worker dies, a supervisor pod inspects `tasks:processing` and re-queues or dead-letters orphaned jobs.

---

## 4. How to Run This Module

```bash
# Run Vitest tests for Lists
npm run test:lists

# Run standalone experiments
npm run exp:lists:feed     # Chronological timeline feed (LPUSH + LTRIM)
npm run exp:lists:queue    # Reliable task queue with atomic LMOVE
```

