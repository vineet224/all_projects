import { describe, it, expect, beforeAll, afterAll, beforeEach } from "vitest";
import { getRedisClient, closeRedisClient, AppRedisClient } from "../../client/redis-client.js";
import { createTestNamespace, TestNamespace } from "../../client/key-manager.js";
import { SortedSetsService } from "./sorted-sets.service.js";

describe("Redis Sorted Sets Operations Lab", () => {
  let client: AppRedisClient;
  let service: SortedSetsService;
  let ns: TestNamespace;

  beforeAll(async () => {
    client = await getRedisClient();
    service = new SortedSetsService(client);
  });

  beforeEach(() => {
    ns = createTestNamespace("sorted-sets");
  });

  afterAll(async () => {
    if (client && client.isOpen) {
      await closeRedisClient();
    }
  });

  // --------------------------------------------------------------------------
  // 1. Core Operations, Scoring & Conditionals
  // --------------------------------------------------------------------------
  describe("1. Core Operations, Scoring & Conditionals", () => {
    it("should add members with scores, retrieve scores, and increment", async () => {
      const key = ns.key("scores");
      await service.zAdd(key, "player_1", 100);
      await service.zAdd(key, "player_2", 250);

      expect(await service.zScore(key, "player_1")).toBe(100);
      expect(await service.zScore(key, "player_missing")).toBeNull();

      const mscores = await service.zMScore(key, ["player_1", "player_2", "player_3"]);
      expect(mscores).toEqual([100, 250, null]);

      // ZINCRBY
      const newScore = await service.zIncrBy(key, "player_1", 50);
      expect(newScore).toBe(150);
      expect(await service.zScore(key, "player_1")).toBe(150);
    });

    it("ZADD with GT (Greater Than) flag only updates score if new score is higher", async () => {
      const key = ns.key("highscores");
      await service.zAdd(key, "player_1", 500);

      // Attempt lower score (300) with GT -> rejected, score remains 500
      await service.zAdd(key, "player_1", 300, { GT: true });
      expect(await service.zScore(key, "player_1")).toBe(500);

      // Attempt higher score (650) with GT -> accepted, updated to 650
      await service.zAdd(key, "player_1", 650, { GT: true });
      expect(await service.zScore(key, "player_1")).toBe(650);
    });

    it("ZCARD and ZCOUNT range queries", async () => {
      const key = ns.key("metrics");
      await service.zAddMultiple(key, [
        { value: "m1", score: 10 },
        { value: "m2", score: 20 },
        { value: "m3", score: 30 },
        { value: "m4", score: 40 },
      ]);

      expect(await service.zCard(key)).toBe(4);
      // Count members with score between 15 and 35
      expect(await service.zCount(key, 15, 35)).toBe(2); // m2 (20), m3 (30)
    });
  });

  // --------------------------------------------------------------------------
  // 2. Ranking, Reverse Ranking & Tie-breaking
  // --------------------------------------------------------------------------
  describe("2. Ranking & Tie-breaking", () => {
    it("ZRANK (ascending) vs ZREVRANK (descending) and tie-breaking lexicographical ordering", async () => {
      const key = ns.key("rankings");
      await service.zAddMultiple(key, [
        { value: "bronze", score: 100 },
        { value: "silver", score: 500 },
        { value: "gold", score: 1000 },
      ]);

      // Ascending (0 is lowest score)
      expect(await service.zRank(key, "bronze")).toBe(0);
      expect(await service.zRank(key, "gold")).toBe(2);

      // Descending (0 is highest score)
      expect(await service.zRevRank(key, "gold")).toBe(0);
      expect(await service.zRevRank(key, "silver")).toBe(1);
      expect(await service.zRevRank(key, "bronze")).toBe(2);

      // Tie-breaking: when scores are identical, Redis orders lexicographically by member name!
      const tieKey = ns.key("ties");
      await service.zAddMultiple(tieKey, [
        { value: "charlie", score: 50 },
        { value: "alice", score: 50 },
        { value: "bob", score: 50 },
      ]);
      // 'alice' < 'bob' < 'charlie'
      const tieAsc = await service.zRangeWithScores(tieKey, 0, -1);
      expect(tieAsc.map((e) => e.value)).toEqual(["alice", "bob", "charlie"]);
    });

    it("ZREVRANGE should extract Top-K items with scores", async () => {
      const key = ns.key("topk");
      await service.zAddMultiple(key, [
        { value: "p1", score: 10 },
        { value: "p2", score: 95 },
        { value: "p3", score: 50 },
        { value: "p4", score: 85 },
      ]);

      const top2 = await service.zRevRangeWithScores(key, 0, 1);
      expect(top2).toEqual([
        { value: "p2", score: 95 },
        { value: "p4", score: 85 },
      ]);
    });
  });

  // --------------------------------------------------------------------------
  // 3. Removal & Pop Operations
  // --------------------------------------------------------------------------
  describe("3. Removal & Pop Operations", () => {
    it("ZREM, ZREMRANGEBYRANK, and ZREMRANGEBYSCORE", async () => {
      const key = ns.key("prune");
      await service.zAddMultiple(key, [
        { value: "a", score: 1 },
        { value: "b", score: 2 },
        { value: "c", score: 3 },
        { value: "d", score: 4 },
        { value: "e", score: 5 },
      ]);

      // Remove specific member
      expect(await service.zRem(key, "c")).toBe(1);

      // Remove lowest score member (rank 0 to 0)
      const removedRank = await service.zRemRangeByRank(key, 0, 0);
      expect(removedRank).toBe(1); // Removed 'a' (score 1)

      // Remove members with score between 4 and 5
      const removedScore = await service.zRemRangeByScore(key, 4, 5);
      expect(removedScore).toBe(2); // Removed 'd', 'e'

      expect(await service.zCard(key)).toBe(1); // Only 'b' remains
    });

    it("ZPOPMIN and ZPOPMAX should atomically extract and remove extremes", async () => {
      const key = ns.key("pop");
      await service.zAddMultiple(key, [
        { value: "low", score: 5 },
        { value: "mid", score: 50 },
        { value: "high", score: 100 },
      ]);

      const minPopped = await service.zPopMin(key, 1);
      expect(minPopped).toEqual([{ value: "low", score: 5 }]);

      const maxPopped = await service.zPopMax(key, 1);
      expect(maxPopped).toEqual([{ value: "high", score: 100 }]);

      expect(await service.zCard(key)).toBe(1);
    });
  });

  // --------------------------------------------------------------------------
  // 4. Encodings & Type Safety
  // --------------------------------------------------------------------------
  describe("4. Encodings & Type Safety", () => {
    it("should use listpack encoding for small sorted sets", async () => {
      const key = ns.key("enc:small");
      await service.zAddMultiple(key, [
        { value: "item1", score: 10 },
        { value: "item2", score: 20 },
      ]);

      const enc = await service.getObjectEncoding(key);
      // In modern Redis, small ZSets use 'listpack' (formerly 'ziplist')
      expect(["listpack", "ziplist"]).toContain(enc);
    });

    it("should throw WRONGTYPE when operating on a Hash or String", async () => {
      const strKey = ns.key("str:type");
      await client.set(strKey, "string value");

      await expect(service.zAdd(strKey, "m", 10)).rejects.toThrow(
        /WRONGTYPE Operation against a key holding the wrong kind of value/i
      );
    });
  });

  // --------------------------------------------------------------------------
  // 5. SDE-2 System Design Patterns
  // --------------------------------------------------------------------------
  describe("5. SDE-2 System Design Patterns", () => {
    it("Gaming Leaderboard: Top-K ranking and player percentile lookups", async () => {
      const boardKey = ns.key("game:leaderboard");
      await service.zAddMultiple(boardKey, [
        { value: "player_alpha", score: 1450 },
        { value: "player_bravo", score: 2100 },
        { value: "player_charlie", score: 850 },
        { value: "player_delta", score: 3200 },
        { value: "player_echo", score: 1900 },
      ]);

      // Query Top 3 players
      const top3 = await service.getTopKLeaderboard(boardKey, 3);
      expect(top3).toEqual([
        { rank: 1, member: "player_delta", score: 3200 },
        { rank: 2, member: "player_bravo", score: 2100 },
        { rank: 3, member: "player_echo", score: 1900 },
      ]);

      // Individual player lookup
      const bravoRank = await service.getPlayerRank(boardKey, "player_bravo");
      expect(bravoRank).toEqual({ rank: 2, score: 2100 });

      const charlieRank = await service.getPlayerRank(boardKey, "player_charlie");
      expect(charlieRank).toEqual({ rank: 5, score: 850 });
    });

    it("Sliding Window Rate Limiter: strictly limits requests over a rolling 500ms window", async () => {
      const rateLimitKey = ns.key("ratelimit:user:123");
      const limit = 3;
      const windowMs = 500;

      // First 3 requests within window -> ALLOWED
      const r1 = await service.checkSlidingWindowRateLimit(rateLimitKey, limit, windowMs);
      const r2 = await service.checkSlidingWindowRateLimit(rateLimitKey, limit, windowMs);
      const r3 = await service.checkSlidingWindowRateLimit(rateLimitKey, limit, windowMs);

      expect(r1.allowed).toBe(true);
      expect(r2.allowed).toBe(true);
      expect(r3.allowed).toBe(true);

      // 4th request exceeds quota -> BLOCKED
      const r4 = await service.checkSlidingWindowRateLimit(rateLimitKey, limit, windowMs);
      expect(r4.allowed).toBe(false);

      // Wait 550ms for window to slide forward
      await new Promise((r) => setTimeout(r, 550));

      // New request in fresh window -> ALLOWED
      const r5 = await service.checkSlidingWindowRateLimit(rateLimitKey, limit, windowMs);
      expect(r5.allowed).toBe(true);
    });
  });
});

