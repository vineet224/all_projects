import { describe, it, expect, beforeAll, afterAll, beforeEach } from "vitest";
import { getRedisClient, closeRedisClient, AppRedisClient, createIsolatedClient } from "../../client/redis-client.js";
import { createTestNamespace, TestNamespace } from "../../client/key-manager.js";
import { StringsService } from "./strings.service.js";

describe("Redis Strings Operations Lab", () => {
  let client: AppRedisClient;
  let service: StringsService;
  let ns: TestNamespace;

  beforeAll(async () => {
    client = await getRedisClient();
    service = new StringsService(client);
  });

  beforeEach(() => {
    ns = createTestNamespace("strings");
  });

  afterAll(async () => {
    if (client && client.isOpen) {
      await closeRedisClient();
    }
  });

  // --------------------------------------------------------------------------
  // 1. Basic Read & Write Operations
  // --------------------------------------------------------------------------
  describe("1. Basic Read/Write & Missing Keys", () => {
    it("should return null when getting a non-existent key", async () => {
      const missingKey = ns.key("missing");
      const value = await service.get(missingKey);
      expect(value).toBeNull();
    });

    it("should set and get simple string values accurately", async () => {
      const key = ns.key("user:name");
      const setReply = await service.set(key, "Alice");
      expect(setReply).toBe("OK");

      const value = await service.get(key);
      expect(value).toBe("Alice");
    });

    it("should handle multi-key MSET and MGET atomically", async () => {
      const key1 = ns.key("item:1");
      const key2 = ns.key("item:2");
      const key3 = ns.key("item:3");

      const msetReply = await service.mSet({
        [key1]: "Apple",
        [key2]: "Banana",
      });
      expect(msetReply).toBe("OK");

      // key3 does not exist, should return null in corresponding array position
      const values = await service.mGet([key1, key3, key2]);
      expect(values).toEqual(["Apple", null, "Banana"]);
    });

    it("should atomically set a new value and return the previous value with SET ... GET", async () => {
      const key = ns.key("config:version");

      // First SET ... GET on non-existent key returns null
      const firstSet = await service.set(key, "v1.0.0", { GET: true });
      expect(firstSet).toBeNull();

      // Second SET ... GET returns previous value 'v1.0.0'
      const secondSet = await service.set(key, "v2.0.0", { GET: true });
      expect(secondSet).toBe("v1.0.0");

      const current = await service.get(key);
      expect(current).toBe("v2.0.0");
    });
  });

  // --------------------------------------------------------------------------
  // 2. Conditional Mutations (NX / XX)
  // --------------------------------------------------------------------------
  describe("2. Conditional Mutations (NX / XX)", () => {
    it("SET NX should succeed only if key does not exist", async () => {
      const key = ns.key("idempotency:token:123");

      // Key does not exist: SET NX succeeds
      const first = await service.set(key, "PROCESSING", { NX: true });
      expect(first).toBe("OK");

      // Key exists: SET NX returns null (operation aborted)
      const second = await service.set(key, "PROCESSING", { NX: true });
      expect(second).toBeNull();

      // Original value preserved
      expect(await service.get(key)).toBe("PROCESSING");
    });

    it("SET XX should succeed only if key already exists", async () => {
      const key = ns.key("cache:profile:99");

      // Key does not exist: SET XX fails
      const initial = await service.set(key, "ProfileData", { XX: true });
      expect(initial).toBeNull();
      expect(await service.get(key)).toBeNull();

      // Create key first
      await service.set(key, "OriginalData");

      // Key exists: SET XX succeeds
      const update = await service.set(key, "UpdatedData", { XX: true });
      expect(update).toBe("OK");
      expect(await service.get(key)).toBe("UpdatedData");
    });

    it("MSETNX should enforce all-or-nothing atomicity across multiple keys", async () => {
      const k1 = ns.key("msetnx:1");
      const k2 = ns.key("msetnx:2");
      const k3 = ns.key("msetnx:3");

      // Case 1: None exist -> all are set
      const batch1 = await service.mSetNx({ [k1]: "V1", [k2]: "V2" });
      expect(batch1).toBe(true);
      expect(await service.get(k1)).toBe("V1");
      expect(await service.get(k2)).toBe("V2");

      // Case 2: k1 already exists, k3 does not exist -> transaction fails, k3 MUST NOT be set
      const batch2 = await service.mSetNx({ [k1]: "OVERWRITE", [k3]: "V3" });
      expect(batch2).toBe(false);

      // Verify all-or-nothing guarantee
      expect(await service.get(k1)).toBe("V1"); // Not overwritten
      expect(await service.get(k3)).toBeNull(); // Not created!
    });
  });

  // --------------------------------------------------------------------------
  // 3. Expiration & TTL Semantics
  // --------------------------------------------------------------------------
  describe("3. Expiration, TTL & Atomic Deletion", () => {
    it("should set key with seconds expiration (EX) and verify TTL", async () => {
      const key = ns.key("session:auth");
      await service.set(key, "user-session-token", { EX: 10 });

      const ttl = await client.ttl(key);
      expect(ttl).toBeGreaterThan(0);
      expect(ttl).toBeLessThanOrEqual(10);
    });

    it("should set key with millisecond expiration (PX) and expire naturally", async () => {
      const key = ns.key("temp:token");
      await service.set(key, "temporary", { PX: 300 });

      const pttl = await client.pTTL(key);
      expect(pttl).toBeGreaterThan(0);
      expect(pttl).toBeLessThanOrEqual(300);

      // Wait for expiration
      await new Promise((resolve) => setTimeout(resolve, 350));
      expect(await service.get(key)).toBeNull();
    });

    it("SET KEEPTTL should preserve existing TTL when updating value", async () => {
      const key = ns.key("cache:stats");
      // Set initial value with 60 second TTL
      await service.set(key, "Count: 1", { EX: 60 });
      const initialTtl = await client.ttl(key);
      expect(initialTtl).toBeGreaterThan(0);

      // Standard SET without KEEPTTL resets TTL to -1 (persistent)!
      // But with KEEPTTL, it retains the remaining TTL
      await service.set(key, "Count: 2", { KEEPTTL: true });
      const preservedTtl = await client.ttl(key);
      expect(preservedTtl).toBeGreaterThan(0);
      expect(preservedTtl).toBeLessThanOrEqual(initialTtl);

      // Contrast: Plain SET strips the TTL
      await service.set(key, "Count: 3");
      const strippedTtl = await client.ttl(key);
      expect(strippedTtl).toBe(-1); // -1 indicates persistent key without expiration
    });

    it("GETDEL should atomically return value and delete key in one roundtrip", async () => {
      const key = ns.key("otp:code");
      await service.set(key, "948215");

      const retrieved = await service.getDel(key);
      expect(retrieved).toBe("948215");

      // Key must now be deleted
      const subsequent = await service.get(key);
      expect(subsequent).toBeNull();
    });

    it("GETEX should atomically return value and update/clear expiration", async () => {
      const key = ns.key("cache:dynamic");
      await service.set(key, "cached-payload");
      expect(await client.ttl(key)).toBe(-1); // No TTL

      // Set TTL to 10 seconds via GETEX
      const val = await service.getEx(key, { EX: 10 });
      expect(val).toBe("cached-payload");
      const ttl = await client.ttl(key);
      expect(ttl).toBeGreaterThan(0);
      expect(ttl).toBeLessThanOrEqual(10);

      // Remove TTL via GETEX PERSIST
      await service.getEx(key, { PERSIST: true });
      expect(await client.ttl(key)).toBe(-1);
    });
  });

  // --------------------------------------------------------------------------
  // 4. Numeric & Counter Operations
  // --------------------------------------------------------------------------
  describe("4. Numeric & Counter Operations", () => {
    it("INCR and DECR should auto-initialize non-existent keys to 0 before operating", async () => {
      const key = ns.key("page:views");
      // First INCR creates key with value 0, then increments to 1
      const count1 = await service.incr(key);
      expect(count1).toBe(1);

      const count2 = await service.incr(key);
      expect(count2).toBe(2);

      const count3 = await service.decr(key);
      expect(count3).toBe(1);
    });

    it("INCRBY and DECRBY should adjust values by arbitrary integers, including negative numbers", async () => {
      const key = ns.key("inventory:stock");
      await service.set(key, 100);

      const afterDeduct = await service.decrBy(key, 15);
      expect(afterDeduct).toBe(85);

      const afterRestock = await service.incrBy(key, 50);
      expect(afterRestock).toBe(135);

      // Decrementing into negative numbers is valid in Redis
      const negative = await service.decrBy(key, 200);
      expect(negative).toBe(-65);
    });

    it("INCRBYFLOAT should handle floating point arithmetic", async () => {
      const key = ns.key("wallet:balance");
      await service.set(key, "10.50");

      const newBal = await service.incrByFloat(key, 0.25);
      expect(newBal).toBe(10.75);

      const decBal = await service.incrByFloat(key, -1.5);
      expect(decBal).toBe(9.25);
    });

    it("should throw an error when executing INCR on a non-numeric string", async () => {
      const key = ns.key("non:numeric");
      await service.set(key, "not-a-number");

      await expect(service.incr(key)).rejects.toThrow(
        /ERR value is not an integer or out of range/i
      );
    });

    it("should throw an error when increment exceeds 64-bit signed integer maximum", async () => {
      const key = ns.key("counter:overflow");
      // Signed 64-bit int max is 9223372036854775807
      await service.set(key, "9223372036854775807");

      await expect(service.incr(key)).rejects.toThrow(
        /ERR increment or decrement would overflow/i
      );
    });
  });

  // --------------------------------------------------------------------------
  // 5. Substring & Offset Operations
  // --------------------------------------------------------------------------
  describe("5. Substring & Offset Operations", () => {
    it("APPEND should concatenate and return the new string length in bytes", async () => {
      const key = ns.key("log:stream");
      const len1 = await service.append(key, "Hello");
      expect(len1).toBe(5);

      const len2 = await service.append(key, " World");
      expect(len2).toBe(11);

      expect(await service.get(key)).toBe("Hello World");
    });

    it("STRLEN should return byte length, noting UTF-8 multi-byte character semantics", async () => {
      const asciiKey = ns.key("str:ascii");
      await service.set(asciiKey, "Antigravity");
      expect(await service.strLen(asciiKey)).toBe(11);

      // UTF-8 edge case: Rocket emoji '🚀' is 4 bytes in UTF-8 encoding
      const utf8Key = ns.key("str:utf8");
      await service.set(utf8Key, "🚀");
      expect(await service.strLen(utf8Key)).toBe(4);
    });

    it("SETRANGE should overwrite in place and zero-pad holes when offset exceeds length", async () => {
      const key = ns.key("range:buffer");
      await service.set(key, "Hello World");

      // Overwrite 'World' with 'Redis' at offset 6
      const newLen = await service.setRange(key, 6, "Redis");
      expect(newLen).toBe(11);
      expect(await service.get(key)).toBe("Hello Redis");

      // Hole padding demonstration: write at offset 15 on a non-existent key
      const holeKey = ns.key("range:hole");
      await service.setRange(holeKey, 5, "DATA");
      // Byte 0-4 are null bytes \x00, followed by DATA
      const val = await service.get(holeKey);
      expect(val).toBe("\x00\x00\x00\x00\x00DATA");
    });

    it("GETRANGE should extract slices with positive and negative indices", async () => {
      const key = ns.key("range:slice");
      await service.set(key, "DistributedSystem");

      // Slice [0, 10] inclusive
      expect(await service.getRange(key, 0, 10)).toBe("Distributed");

      // Slice with negative index: -6 to -1 is the last 6 characters
      expect(await service.getRange(key, -6, -1)).toBe("System");

      // Start > End returns empty string
      expect(await service.getRange(key, 5, 2)).toBe("");
    });
  });

  // --------------------------------------------------------------------------
  // 6. Bitmaps & Bitwise Operations
  // --------------------------------------------------------------------------
  describe("6. Bitmaps & Bitwise Operations", () => {
    it("SETBIT and GETBIT should manipulate individual bits", async () => {
      const key = ns.key("user:active:bitmap");

      // Set bit 7 to 1 (offset 7 in byte 0 -> binary 00000001 = ASCII byte 1)
      const prev1 = await service.setBit(key, 7, 1);
      expect(prev1).toBe(0);

      expect(await service.getBit(key, 7)).toBe(1);
      expect(await service.getBit(key, 6)).toBe(0); // Unset bit returns 0

      // Overwriting returns old bit value
      const prev2 = await service.setBit(key, 7, 0);
      expect(prev2).toBe(1);
      expect(await service.getBit(key, 7)).toBe(0);
    });

    it("BITCOUNT should count total set bits (population count)", async () => {
      const key = ns.key("flags:users");
      // Set bits at positions 10, 20, 30, 40
      await service.setBit(key, 10, 1);
      await service.setBit(key, 20, 1);
      await service.setBit(key, 30, 1);
      await service.setBit(key, 40, 1);

      const totalBits = await service.bitCount(key);
      expect(totalBits).toBe(4);
    });

    it("BITPOS should locate first occurrence of 0 or 1", async () => {
      const key = ns.key("bitpos:sample");
      // Set bit 5 to 1
      await service.setBit(key, 5, 1);

      // First 1 bit is at position 5
      const firstOne = await service.bitPos(key, 1);
      expect(firstOne).toBe(5);

      // First 0 bit is at position 0
      const firstZero = await service.bitPos(key, 0);
      expect(firstZero).toBe(0);
    });

    it("BITOP AND / OR should perform cohort intersection and union across bitsets", async () => {
      const day1 = ns.key("dau:day1");
      const day2 = ns.key("dau:day2");
      const activeBothDays = ns.key("dau:retained");

      // Day 1 active user IDs: 10, 25, 50
      await service.setBit(day1, 10, 1);
      await service.setBit(day1, 25, 1);
      await service.setBit(day1, 50, 1);

      // Day 2 active user IDs: 25, 50, 75
      await service.setBit(day2, 25, 1);
      await service.setBit(day2, 50, 1);
      await service.setBit(day2, 75, 1);

      // Perform BITOP AND to find retained users (active on BOTH days: 25 and 50)
      await service.bitOp("AND", activeBothDays, [day1, day2]);

      const retainedCount = await service.bitCount(activeBothDays);
      expect(retainedCount).toBe(2);
      expect(await service.getBit(activeBothDays, 25)).toBe(1);
      expect(await service.getBit(activeBothDays, 50)).toBe(1);
      expect(await service.getBit(activeBothDays, 10)).toBe(0);
    });
  });

  // --------------------------------------------------------------------------
  // 7. Encodings & Memory Internals
  // --------------------------------------------------------------------------
  describe("7. Encodings & SDS Memory Internals", () => {
    it("should demonstrate INT, EMBSTR, and RAW encoding thresholds", async () => {
      const intKey = ns.key("int");
      const embstrKey = ns.key("emb");
      const rawKey = ns.key("raw");

      // 1. Integer value fits in 64-bit int -> 'int' encoding
      await service.set(intKey, 123456);
      const intEnc = await service.getObjectEncoding(intKey);
      expect(intEnc).toBe("int");

      // 2. Short string under encoding threshold -> 'embstr' encoding (single allocation for redisObject + SDS)
      await service.set(embstrKey, "hello");
      const embstrEnc = await service.getObjectEncoding(embstrKey);
      expect(embstrEnc).toBe("embstr");

      // 3. String > 44 bytes -> 'raw' encoding (two allocations: redisObject + SDS)
      const longString = "A".repeat(50);
      await service.set(rawKey, longString);
      const rawEnc = await service.getObjectEncoding(rawKey);
      expect(rawEnc).toBe("raw");

      // 4. Modifying an embstr string automatically converts it to 'raw' because embstr is immutable
      await service.append(embstrKey, " world");
      const afterAppendEnc = await service.getObjectEncoding(embstrKey);
      expect(afterAppendEnc).toBe("raw");
    });
  });

  // --------------------------------------------------------------------------
  // 8. Error Conditions & Wrong Data Types
  // --------------------------------------------------------------------------
  describe("8. Error Handling & Wrong Data Types", () => {
    it("should throw WRONGTYPE when attempting string command on a Hash or Set", async () => {
      const hashKey = ns.key("struct:hash");
      // Create a Redis Hash
      await client.hSet(hashKey, "field1", "val1");

      // Executing GET (string command) on a Hash key throws WRONGTYPE
      await expect(service.get(hashKey)).rejects.toThrow(
        /WRONGTYPE Operation against a key holding the wrong kind of value/i
      );

      // Executing INCR on a Hash key throws WRONGTYPE
      await expect(service.incr(hashKey)).rejects.toThrow(
        /WRONGTYPE Operation against a key holding the wrong kind of value/i
      );
    });
  });

  // --------------------------------------------------------------------------
  // 9. SDE-2 System Design Patterns
  // --------------------------------------------------------------------------
  describe("9. SDE-2 System Design Patterns", () => {
    it("Distributed Lock: should guarantee mutual exclusion and safe release via Lua", async () => {
      const lockKey = ns.key("lock:order:1001");
      const clientA_Id = "worker-node-A";
      const clientB_Id = "worker-node-B";

      // Worker A acquires lock for 2000ms
      const acquiredA = await service.acquireLock(lockKey, clientA_Id, 2000);
      expect(acquiredA).toBe(true);

      // Worker B attempts to acquire the same lock -> fails (mutual exclusion)
      const acquiredB = await service.acquireLock(lockKey, clientB_Id, 2000);
      expect(acquiredB).toBe(false);

      // Worker B attempts to release Worker A's lock -> fails (safe release)
      const releaseB = await service.releaseLock(lockKey, clientB_Id);
      expect(releaseB).toBe(false);

      // Worker A releases its own lock -> succeeds
      const releaseA = await service.releaseLock(lockKey, clientA_Id);
      expect(releaseA).toBe(true);

      // Lock is now free; Worker B can acquire it
      const reacquireB = await service.acquireLock(lockKey, clientB_Id, 2000);
      expect(reacquireB).toBe(true);

      // Clean up
      await service.releaseLock(lockKey, clientB_Id);
    });

    it("Rate Limiter: should enforce request quotas and reset on window rollover", async () => {
      const userKey = ns.key("ratelimit:user:42");
      const limit = 3;
      const windowSec = 1;

      // First 3 requests should be allowed
      const r1 = await service.checkRateLimit(userKey, limit, windowSec);
      expect(r1.allowed).toBe(true);
      expect(r1.currentCount).toBe(1);
      expect(r1.remaining).toBe(2);

      const r2 = await service.checkRateLimit(userKey, limit, windowSec);
      expect(r2.allowed).toBe(true);
      expect(r2.currentCount).toBe(2);

      const r3 = await service.checkRateLimit(userKey, limit, windowSec);
      expect(r3.allowed).toBe(true);
      expect(r3.currentCount).toBe(3);
      expect(r3.remaining).toBe(0);

      // 4th request exceeds limit
      const r4 = await service.checkRateLimit(userKey, limit, windowSec);
      expect(r4.allowed).toBe(false);
      expect(r4.currentCount).toBe(4);
      expect(r4.remaining).toBe(0);

      // Wait for window to roll over
      await new Promise((resolve) => setTimeout(resolve, 1100));

      // After window expiration, request should be allowed again
      const r5 = await service.checkRateLimit(userKey, limit, windowSec);
      expect(r5.allowed).toBe(true);
      expect(r5.currentCount).toBe(1);
    });
  });
});
