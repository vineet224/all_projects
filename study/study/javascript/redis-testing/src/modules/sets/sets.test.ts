import { describe, it, expect, beforeAll, afterAll, beforeEach } from "vitest";
import { getRedisClient, closeRedisClient, AppRedisClient } from "../../client/redis-client.js";
import { createTestNamespace, TestNamespace } from "../../client/key-manager.js";
import { SetsService } from "./sets.service.js";

describe("Redis Sets Operations Lab", () => {
  let client: AppRedisClient;
  let service: SetsService;
  let ns: TestNamespace;

  beforeAll(async () => {
    client = await getRedisClient();
    service = new SetsService(client);
  });

  beforeEach(() => {
    ns = createTestNamespace("sets");
  });

  afterAll(async () => {
    if (client && client.isOpen) {
      await closeRedisClient();
    }
  });

  // --------------------------------------------------------------------------
  // 1. Core Set Operations & Uniqueness
  // --------------------------------------------------------------------------
  describe("1. Core Set Operations & Uniqueness", () => {
    it("SADD should enforce uniqueness and ignore duplicate members", async () => {
      const key = ns.key("tags");
      // Add initial members
      const added1 = await service.sAdd(key, ["redis", "database", "cache"]);
      expect(added1).toBe(3);

      // Attempt to add duplicate 'redis' and new 'nosql'
      const added2 = await service.sAdd(key, ["redis", "nosql"]);
      expect(added2).toBe(1); // Only 'nosql' was added

      expect(await service.sCard(key)).toBe(4);
    });

    it("SISMEMBER and SMISMEMBER member verification", async () => {
      const key = ns.key("whitelist");
      await service.sAdd(key, ["ip_1", "ip_2", "ip_3"]);

      expect(await service.sIsMember(key, "ip_2")).toBe(true);
      expect(await service.sIsMember(key, "ip_99")).toBe(false);

      const batchCheck = await service.sMIsMember(key, ["ip_1", "ip_missing", "ip_3"]);
      expect(batchCheck).toEqual([true, false, true]);
    });

    it("SREM and SCARD operations", async () => {
      const key = ns.key("users");
      await service.sAdd(key, ["alice", "bob", "charlie"]);

      const removed = await service.sRem(key, ["bob", "david"]);
      expect(removed).toBe(1); // Only bob was in set

      expect(await service.sCard(key)).toBe(2);
      expect(await service.sIsMember(key, "bob")).toBe(false);
    });

    it("SMEMBERS on non-existent key returns empty array", async () => {
      const key = ns.key("non_existent");
      expect(await service.sMembers(key)).toEqual([]);
    });
  });

  // --------------------------------------------------------------------------
  // 2. Random Sampling (Giveaway / Lottery Engine)
  // --------------------------------------------------------------------------
  describe("2. Random Sampling (SPOP vs SRANDMEMBER)", () => {
    it("SPOP should remove and return random member (without replacement)", async () => {
      const key = ns.key("raffle");
      await service.sAdd(key, ["ticket_1", "ticket_2", "ticket_3"]);

      const winner = await service.sPop(key);
      expect(typeof winner).toBe("string");
      expect(["ticket_1", "ticket_2", "ticket_3"]).toContain(winner);

      // Winner is removed from raffle pool
      expect(await service.sCard(key)).toBe(2);
      expect(await service.sIsMember(key, winner as string)).toBe(false);
    });

    it("SRANDMEMBER should return random member without removing it (with replacement)", async () => {
      const key = ns.key("pool");
      await service.sAdd(key, ["item_A", "item_B", "item_C"]);

      const sampled = await service.sRandMember(key);
      expect(typeof sampled).toBe("string");
      expect(["item_A", "item_B", "item_C"]).toContain(sampled);

      // Set cardinality remains 3
      expect(await service.sCard(key)).toBe(3);
    });
  });

  // --------------------------------------------------------------------------
  // 3. Set Algebra: Intersection, Union, Difference
  // --------------------------------------------------------------------------
  describe("3. Set Algebra (SINTER, SUNION, SDIFF)", () => {
    it("SINTER, SUNION, and SDIFF computations", async () => {
      const setA = ns.key("set:A");
      const setB = ns.key("set:B");

      await service.sAdd(setA, ["apple", "banana", "cherry"]);
      await service.sAdd(setB, ["banana", "cherry", "date"]);

      // Intersection (A ∩ B)
      const intersection = await service.sInter([setA, setB]);
      expect(intersection.sort()).toEqual(["banana", "cherry"]);

      // Union (A ∪ B)
      const union = await service.sUnion([setA, setB]);
      expect(union.sort()).toEqual(["apple", "banana", "cherry", "date"]);

      // Difference (A - B): in A but NOT in B
      const diff = await service.sDiff([setA, setB]);
      expect(diff).toEqual(["apple"]);

      // SINTERCARD: returns count without transferring payload
      const interCard = await service.sInterCard([setA, setB]);
      expect(interCard).toBe(2);
    });

    it("SINTERSTORE should store result directly in a new key in Redis", async () => {
      const setA = ns.key("group1");
      const setB = ns.key("group2");
      const dest = ns.key("dest:inter");

      await service.sAdd(setA, ["u1", "u2", "u3"]);
      await service.sAdd(setB, ["u2", "u3", "u4"]);

      const storedCount = await service.sInterStore(dest, [setA, setB]);
      expect(storedCount).toBe(2);

      const members = await service.sMembers(dest);
      expect(members.sort()).toEqual(["u2", "u3"]);
    });
  });

  // --------------------------------------------------------------------------
  // 4. Encodings & Type Safety
  // --------------------------------------------------------------------------
  describe("4. Encodings & Type Safety", () => {
    it("should use intset encoding for integer-only sets", async () => {
      const key = ns.key("enc:integers");
      await service.sAdd(key, ["10", "20", "30", "40"]);

      const enc = await service.getObjectEncoding(key);
      expect(enc).toBe("intset");

      // Adding a non-numeric string converts it away from intset
      await service.sAdd(key, "not_a_number");
      const afterEnc = await service.getObjectEncoding(key);
      expect(["hashtable", "listpack"]).toContain(afterEnc);
    });

    it("should throw WRONGTYPE when running SADD against a String", async () => {
      const strKey = ns.key("str:type");
      await client.set(strKey, "raw string");

      await expect(service.sAdd(strKey, "member")).rejects.toThrow(
        /WRONGTYPE Operation against a key holding the wrong kind of value/i
      );
    });
  });

  // --------------------------------------------------------------------------
  // 5. SDE-2 System Design Patterns
  // --------------------------------------------------------------------------
  describe("5. SDE-2 System Design Patterns", () => {
    it("Social Graph: Mutual Friends & Recommendation Engine", async () => {
      const userA = "alice";
      const userB = "bob";

      // Alice follows: Charlie, David, Eve
      await service.followUser(userA, "charlie");
      await service.followUser(userA, "david");
      await service.followUser(userA, "eve");

      // Bob follows: David, Eve, Frank, Grace
      await service.followUser(userB, "david");
      await service.followUser(userB, "eve");
      await service.followUser(userB, "frank");
      await service.followUser(userB, "grace");

      // Mutual connections (Alice ∩ Bob): David and Eve
      const mutual = await service.getMutualFollowing(userA, userB);
      expect(mutual.sort()).toEqual(["david", "eve"]);

      // Recommendations for Alice (Bob's followings that Alice doesn't follow: Frank, Grace)
      const recommendations = await service.getRecommendedFollows(userA, userB);
      expect(recommendations.sort()).toEqual(["frank", "grace"]);

      // Unfollow
      await service.unfollowUser(userA, "charlie");
      const remaining = await client.sMembers(`user:${userA}:following`);
      expect(remaining.sort()).toEqual(["david", "eve"]);

      // Clean up manually created user keys
      await client.del([
        `user:${userA}:following`,
        `user:${userB}:following`,
        "user:charlie:followers",
        "user:david:followers",
        "user:eve:followers",
        "user:frank:followers",
        "user:grace:followers",
      ]);
    });
  });
});

