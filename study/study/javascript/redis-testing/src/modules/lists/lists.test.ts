import { describe, it, expect, beforeAll, afterAll, beforeEach } from "vitest";
import { getRedisClient, closeRedisClient, AppRedisClient } from "../../client/redis-client.js";
import { createTestNamespace, TestNamespace } from "../../client/key-manager.js";
import { ListsService } from "./lists.service.js";

describe("Redis Lists Operations Lab", () => {
  let client: AppRedisClient;
  let service: ListsService;
  let ns: TestNamespace;

  beforeAll(async () => {
    client = await getRedisClient();
    service = new ListsService(client);
  });

  beforeEach(() => {
    ns = createTestNamespace("lists");
  });

  afterAll(async () => {
    if (client && client.isOpen) {
      await closeRedisClient();
    }
  });

  // --------------------------------------------------------------------------
  // 1. Core Push/Pop Operations (FIFO Queue vs LIFO Stack)
  // --------------------------------------------------------------------------
  describe("1. Core Push/Pop Operations", () => {
    it("FIFO Queue pattern: LPUSH + RPOP maintains First-In-First-Out ordering", async () => {
      const queueKey = ns.key("fifo:queue");
      await service.lPush(queueKey, ["msg_1", "msg_2", "msg_3"]); // msg_3 at head, msg_1 at tail

      expect(await service.rPop(queueKey)).toBe("msg_1");
      expect(await service.rPop(queueKey)).toBe("msg_2");
      expect(await service.rPop(queueKey)).toBe("msg_3");
      expect(await service.rPop(queueKey)).toBeNull(); // Empty list returns null
    });

    it("LIFO Stack pattern: LPUSH + LPOP maintains Last-In-First-Out ordering", async () => {
      const stackKey = ns.key("lifo:stack");
      await service.lPush(stackKey, "call_1");
      await service.lPush(stackKey, "call_2");

      expect(await service.lPop(stackKey)).toBe("call_2");
      expect(await service.lPop(stackKey)).toBe("call_1");
    });

    it("RPUSH appends to tail and LLEN returns total element count", async () => {
      const key = ns.key("append:list");
      await service.rPush(key, ["alpha", "bravo", "charlie"]);

      expect(await service.lLen(key)).toBe(3);
      expect(await service.lIndex(key, 0)).toBe("alpha");
      expect(await service.lIndex(key, -1)).toBe("charlie"); // -1 is last element
    });
  });

  // --------------------------------------------------------------------------
  // 2. Inspection, Trimming & Modification
  // --------------------------------------------------------------------------
  describe("2. Inspection, Trimming & Modification", () => {
    it("LRANGE slices and negative index boundaries", async () => {
      const key = ns.key("range:list");
      await service.rPush(key, ["zero", "one", "two", "three", "four"]);

      // Full slice
      expect(await service.lRange(key, 0, -1)).toEqual(["zero", "one", "two", "three", "four"]);
      // Sub-slice
      expect(await service.lRange(key, 1, 3)).toEqual(["one", "two", "three"]);
      // Out of bounds start returns empty array
      expect(await service.lRange(key, 10, 20)).toEqual([]);
    });

    it("LTRIM caps list to specified range, discarding all other elements", async () => {
      const key = ns.key("trim:list");
      await service.rPush(key, ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9"]);

      // Keep only top 3 elements (indices 0 to 2)
      await service.lTrim(key, 0, 2);
      expect(await service.lLen(key)).toBe(3);
      expect(await service.lRange(key, 0, -1)).toEqual(["0", "1", "2"]);
    });

    it("LSET updates element in place, throws error if index out of bounds", async () => {
      const key = ns.key("set:list");
      await service.rPush(key, ["itemA", "itemB"]);

      await service.lSet(key, 1, "itemB_modified");
      expect(await service.lIndex(key, 1)).toBe("itemB_modified");

      // Index out of range throws error
      await expect(service.lSet(key, 10, "overflow")).rejects.toThrow(
        /ERR index out of range/i
      );
    });

    it("LREM removes specific element occurrences", async () => {
      const key = ns.key("rem:list");
      await service.rPush(key, ["apple", "banana", "apple", "cherry", "apple"]);

      // Remove 2 occurrences of 'apple' from head to tail
      const removed = await service.lRem(key, 2, "apple");
      expect(removed).toBe(2);
      expect(await service.lRange(key, 0, -1)).toEqual(["banana", "cherry", "apple"]);
    });
  });

  // --------------------------------------------------------------------------
  // 3. Reliable List Transfer (LMOVE)
  // --------------------------------------------------------------------------
  describe("3. Reliable List Transfer (LMOVE)", () => {
    it("LMOVE atomically transfers element between lists", async () => {
      const source = ns.key("queue:pending");
      const dest = ns.key("queue:processing");

      await service.lPush(source, ["job_1", "job_2"]);

      // Move job from source tail to dest head
      const moved = await service.lMove(source, dest, "RIGHT", "LEFT");
      expect(moved).toBe("job_1");

      expect(await service.lRange(source, 0, -1)).toEqual(["job_2"]);
      expect(await service.lRange(dest, 0, -1)).toEqual(["job_1"]);
    });
  });

  // --------------------------------------------------------------------------
  // 4. Encodings & Type Safety
  // --------------------------------------------------------------------------
  describe("4. Encodings & Type Safety", () => {
    it("should use listpack/quicklist encoding for Redis lists", async () => {
      const key = ns.key("enc:list");
      await service.rPush(key, ["v1", "v2", "v3"]);

      const enc = await service.getObjectEncoding(key);
      // In modern Redis (7.0+), small lists are encoded as 'listpack', transitioning to 'quicklist' as size grows
      expect(["quicklist", "listpack"]).toContain(enc);
    });

    it("should throw WRONGTYPE when calling list command on a String", async () => {
      const strKey = ns.key("str:type");
      await client.set(strKey, "raw string");

      await expect(service.lPush(strKey, "element")).rejects.toThrow(
        /WRONGTYPE Operation against a key holding the wrong kind of value/i
      );
    });
  });

  // --------------------------------------------------------------------------
  // 5. SDE-2 System Design Patterns
  // --------------------------------------------------------------------------
  describe("5. SDE-2 System Design Patterns", () => {
    it("Chronological Feed: keeps bounded recent posts with paginated retrieval", async () => {
      const userId = "alice";
      // Push 5 posts with max size capped to 3
      for (let i = 1; i <= 5; i++) {
        await service.pushTimelinePost(
          userId,
          {
            postId: `post_${i}`,
            authorId: "tech_insider",
            content: `Update #${i}`,
            timestamp: Date.now() + i,
          },
          3 // Max 3 items retained
        );
      }

      // Timeline should contain ONLY the 3 latest posts (post_5, post_4, post_3)
      const feed = await service.getTimelineFeed(userId, 0, 10);
      expect(feed.length).toBe(3);
      expect(feed[0].postId).toBe("post_5");
      expect(feed[1].postId).toBe("post_4");
      expect(feed[2].postId).toBe("post_3");

      // Clean up timeline key
      await client.del(`timeline:${userId}`);
    });

    it("Reliable Task Queue: ensures no job is lost on worker failure via atomic LMOVE", async () => {
      const pendingQ = ns.key("tasks:pending");
      const processingQ = ns.key("tasks:processing");

      const taskA = { jobId: "job_101", payload: { orderId: 99 }, enqueuedAt: Date.now() };
      const taskB = { jobId: "job_102", payload: { orderId: 100 }, enqueuedAt: Date.now() };

      await service.enqueueTask(pendingQ, taskA);
      await service.enqueueTask(pendingQ, taskB);

      // Worker 1 claims oldest task
      const claimed = await service.claimTask(pendingQ, processingQ);
      expect(claimed?.jobId).toBe("job_101");

      // Job 101 is now safely in processing queue; if worker crashes, job is NOT lost!
      expect(await service.lLen(processingQ)).toBe(1);

      // Worker successfully finishes task and acknowledges
      await service.completeTask(processingQ, claimed!);
      expect(await service.lLen(processingQ)).toBe(0);
    });
  });
});
