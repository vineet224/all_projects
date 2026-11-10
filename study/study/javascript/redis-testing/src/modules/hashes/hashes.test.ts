import { describe, it, expect, beforeAll, afterAll, beforeEach } from "vitest";
import { getRedisClient, closeRedisClient, AppRedisClient } from "../../client/redis-client.js";
import { createTestNamespace, TestNamespace } from "../../client/key-manager.js";
import { HashesService } from "./hashes.service.js";

describe("Redis Hashes Operations Lab", () => {
  let client: AppRedisClient;
  let service: HashesService;
  let ns: TestNamespace;

  beforeAll(async () => {
    client = await getRedisClient();
    service = new HashesService(client);
  });

  beforeEach(() => {
    ns = createTestNamespace("hashes");
  });

  afterAll(async () => {
    if (client && client.isOpen) {
      await closeRedisClient();
    }
  });

  // --------------------------------------------------------------------------
  // 1. Basic Field Operations & Missing Fields/Keys
  // --------------------------------------------------------------------------
  describe("1. Basic Field Read/Write & Missing Keys", () => {
    it("should return null for non-existent field and empty object for non-existent key", async () => {
      const key = ns.key("missing:hash");
      expect(await service.hGet(key, "non_existent_field")).toBeNull();
      expect(await service.hGetAll(key)).toEqual({});
    });

    it("should set and get single and multiple fields accurately", async () => {
      const key = ns.key("user:101");

      // HSET single field
      const added = await service.hSet(key, "username", "alice");
      expect(added).toBe(1); // 1 new field added

      // Update existing field returns 0 added
      const updated = await service.hSet(key, "username", "alice_updated");
      expect(updated).toBe(0);

      // Multi-field HSET
      await service.hSetMultiple(key, {
        email: "alice@example.com",
        age: 28,
        city: "Seattle",
      });

      // HMGET retrieves selected fields; missing field returns null
      const values = await service.hmGet(key, ["username", "email", "missingField", "city"]);
      expect(values).toEqual(["alice_updated", "email: alice@example.com".replace("email: ", ""), null, "Seattle"]);

      // HGETALL returns entire field-value mapping
      const all = await service.hGetAll(key);
      expect(all).toEqual({
        username: "alice_updated",
        email: "alice@example.com",
        age: "28",
        city: "Seattle",
      });
    });

    it("HSETNX should conditionally set field only if it does not already exist", async () => {
      const key = ns.key("settings:1");
      const first = await service.hSetNx(key, "theme", "dark");
      expect(first).toBe(true);

      const second = await service.hSetNx(key, "theme", "light");
      expect(second).toBe(false);

      expect(await service.hGet(key, "theme")).toBe("dark");
    });
  });

  // --------------------------------------------------------------------------
  // 2. Inspection, Deletion & Cursor Iteration
  // --------------------------------------------------------------------------
  describe("2. Inspection, Deletion & Cursor Iteration", () => {
    it("HEXISTS, HDEL, HLEN, and HSTRLEN operations", async () => {
      const key = ns.key("metadata");
      await service.hSetMultiple(key, {
        f1: "small",
        f2: "a_longer_string_payload",
      });

      expect(await service.hExists(key, "f1")).toBe(true);
      expect(await service.hExists(key, "f3")).toBe(false);
      expect(await service.hLen(key)).toBe(2);
      expect(await service.hStrLen(key, "f2")).toBe(23);

      // HDEL removes fields and returns deleted count
      const deleted = await service.hDel(key, ["f1", "f3"]);
      expect(deleted).toBe(1); // Only f1 existed
      expect(await service.hExists(key, "f1")).toBe(false);
      expect(await service.hLen(key)).toBe(1);
    });

    it("HKEYS and HVALS inspection", async () => {
      const key = ns.key("roles");
      await service.hSetMultiple(key, { admin: "1", editor: "2", viewer: "3" });

      const keys = await service.hKeys(key);
      const vals = await service.hVals(key);
      expect(keys.sort()).toEqual(["admin", "editor", "viewer"]);
      expect(vals.sort()).toEqual(["1", "2", "3"]);
    });

    it("HSCAN should iterate through fields using cursor without blocking Redis", async () => {
      const key = ns.key("large:hash");
      const record: Record<string, string> = {};
      for (let i = 0; i < 20; i++) {
        record[`field_${i}`] = `val_${i}`;
      }
      await service.hSetMultiple(key, record);

      // HSCAN with MATCH and COUNT
      const scanResult = await service.hScan(key, 0, { COUNT: 10 });
      expect(scanResult.entries.length).toBeGreaterThan(0);
      expect(scanResult.entries[0]).toHaveProperty("field");
      expect(scanResult.entries[0]).toHaveProperty("value");
    });
  });

  // --------------------------------------------------------------------------
  // 3. Numeric Counter Operations
  // --------------------------------------------------------------------------
  describe("3. Numeric Counter Operations", () => {
    it("HINCRBY and HINCRBYFLOAT auto-initialize non-existent fields to 0", async () => {
      const key = ns.key("metrics:page");

      // Auto-initializes to 0, increments to 10
      const count = await service.hIncrBy(key, "hits", 10);
      expect(count).toBe(10);

      const floatVal = await service.hIncrByFloat(key, "score", 3.14);
      expect(floatVal).toBe(3.14);

      // Negative increment
      const decreased = await service.hIncrBy(key, "hits", -4);
      expect(decreased).toBe(6);
    });

    it("should throw error when executing HINCRBY on non-integer field", async () => {
      const key = ns.key("type:err");
      await service.hSet(key, "status", "active");

      await expect(service.hIncrBy(key, "status", 1)).rejects.toThrow(
        /ERR hash value is not an integer/i
      );
    });
  });

  // --------------------------------------------------------------------------
  // 4. Redis 7.4+ Field-Level Expiration (HEXPIRE & HTTL)
  // --------------------------------------------------------------------------
  describe("4. Field-Level Expiration (HEXPIRE / HTTL)", () => {
    it("should expire specific fields independently while preserving other hash fields", async () => {
      const key = ns.key("session:entity");
      await service.hSetMultiple(key, {
        persistentData: "permanent_profile",
        tempAuthToken: "short_lived_token",
      });

      // Set 1-second TTL only on 'tempAuthToken'
      const expireReply = await service.hExpire(key, 1, "tempAuthToken");
      expect(expireReply).toEqual([1]); // 1 indicates expiration set

      // Check field TTL
      const ttlReply = await service.hTtl(key, ["tempAuthToken", "persistentData"]);
      expect(ttlReply[0]).toBeGreaterThan(0);
      expect(ttlReply[1]).toBe(-1); // -1 indicates persistent field

      // Wait 1.1s for tempAuthToken to expire
      await new Promise((r) => setTimeout(r, 1100));

      // tempAuthToken must be expired/removed, while persistentData remains intact!
      expect(await service.hGet(key, "tempAuthToken")).toBeNull();
      expect(await service.hGet(key, "persistentData")).toBe("permanent_profile");
      expect(await service.hLen(key)).toBe(1);
    });
  });

  // --------------------------------------------------------------------------
  // 5. Memory Encodings & Type Safety
  // --------------------------------------------------------------------------
  describe("5. Encodings & Type Safety", () => {
    it("should use listpack encoding for compact hashes", async () => {
      const key = ns.key("enc:compact");
      await service.hSetMultiple(key, { a: "1", b: "2", c: "3" });

      const encoding = await service.getObjectEncoding(key);
      // In Redis 7.0+, small hashes use 'listpack' (formerly 'ziplist')
      expect(encoding).toBe("listpack");
    });

    it("should throw WRONGTYPE when calling hash commands on a String key", async () => {
      const strKey = ns.key("str:wrong");
      await client.set(strKey, "I am a string");

      await expect(service.hSet(strKey, "field", "val")).rejects.toThrow(
        /WRONGTYPE Operation against a key holding the wrong kind of value/i
      );
    });
  });

  // --------------------------------------------------------------------------
  // 6. SDE-2 System Design Patterns
  // --------------------------------------------------------------------------
  describe("6. SDE-2 System Design Patterns", () => {
    it("User Profile: stores structured entity and supports atomic login counter updates", async () => {
      const profile = {
        id: "usr_99",
        username: "jdoe",
        email: "jdoe@tech.io",
        loginCount: 5,
        lastLoginAt: Date.now() - 3600000,
        tier: "pro" as const,
      };

      await service.saveUserProfile(profile);
      const fetched = await service.getUserProfile(profile.id);
      expect(fetched).toEqual(profile);

      // Record a login event atomically
      const loginResult = await service.recordUserLogin(profile.id);
      expect(loginResult.loginCount).toBe(6);
      expect(loginResult.lastLoginAt).toBeGreaterThan(profile.lastLoginAt);
    });

    it("Shopping Cart: atomically updates quantities and purges zeroed items", async () => {
      const cartKey = ns.key("cart:user:55");

      // Add item 101 with quantity 2
      await service.updateCartItem(cartKey, "item_101", 2, 25.5);
      // Add item 202 with quantity 1
      await service.updateCartItem(cartKey, "item_202", 1, 10.0);

      let summary = await service.getCartSummary(cartKey);
      expect(summary.items.length).toBe(2);
      expect(summary.total).toBe(61.0); // 2*25.5 + 1*10.0 = 61.0

      // Decrement item 202 by 1 -> quantity reaches 0, auto-removed
      const updateResult = await service.updateCartItem(cartKey, "item_202", -1, 10.0);
      expect(updateResult.quantity).toBe(0);
      expect(updateResult.removed).toBe(true);

      summary = await service.getCartSummary(cartKey);
      expect(summary.items.length).toBe(1);
      expect(summary.items[0].itemId).toBe("item_101");
      expect(summary.total).toBe(51.0);
    });
  });
});

