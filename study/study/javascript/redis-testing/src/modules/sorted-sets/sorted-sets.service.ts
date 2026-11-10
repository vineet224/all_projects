import { AppRedisClient } from "../../client/redis-client.js";

export interface LeaderboardEntry {
  rank: number;
  member: string;
  score: number;
}

export interface SlidingWindowRateLimitResult {
  allowed: boolean;
  currentCount: number;
  remaining: number;
  resetTimeMs: number;
}

export class SortedSetsService {
  constructor(private client: AppRedisClient) {}

  // --------------------------------------------------------------------------
  // 1. Core Sorted Set Operations
  // --------------------------------------------------------------------------

  async zAdd(
    key: string,
    member: string,
    score: number,
    options?: {
      NX?: boolean;
      XX?: boolean;
      GT?: boolean;
      LT?: boolean;
      CH?: boolean;
    }
  ): Promise<number> {
    return await this.client.zAdd(key, [{ value: member, score }], options as any);
  }

  async zAddMultiple(
    key: string,
    members: { value: string; score: number }[],
    options?: {
      NX?: boolean;
      XX?: boolean;
      GT?: boolean;
      LT?: boolean;
      CH?: boolean;
    }
  ): Promise<number> {
    return await this.client.zAdd(key, members, options as any);
  }

  async zScore(key: string, member: string): Promise<number | null> {
    const res = await this.client.zScore(key, member);
    return res !== null && res !== undefined ? res : null;
  }

  async zMScore(key: string, members: string[]): Promise<(number | null)[]> {
    if (members.length === 0) return [];
    const res = await this.client.zmScore(key, members);
    return res.map((s) => (s !== undefined && s !== null ? s : null));
  }

  async zIncrBy(key: string, member: string, increment: number): Promise<number> {
    return await this.client.zIncrBy(key, increment, member);
  }

  async zCard(key: string): Promise<number> {
    return await this.client.zCard(key);
  }

  async zCount(key: string, min: number | string, max: number | string): Promise<number> {
    return await this.client.zCount(key, min, max);
  }

  // --------------------------------------------------------------------------
  // 2. Ranking & Range Queries
  // --------------------------------------------------------------------------

  async zRank(key: string, member: string): Promise<number | null> {
    const rank = await this.client.zRank(key, member);
    return rank !== null && rank !== undefined ? rank : null;
  }

  async zRevRank(key: string, member: string): Promise<number | null> {
    const rank = await this.client.zRevRank(key, member);
    return rank !== null && rank !== undefined ? rank : null;
  }

  async zRangeWithScores(
    key: string,
    start: number,
    stop: number
  ): Promise<{ value: string; score: number }[]> {
    return await this.client.zRangeWithScores(key, start, stop);
  }

  async zRevRangeWithScores(
    key: string,
    start: number,
    stop: number
  ): Promise<{ value: string; score: number }[]> {
    return await this.client.zRangeWithScores(key, start, stop, { REV: true });
  }

  // --------------------------------------------------------------------------
  // 3. Removal & Pop Operations
  // --------------------------------------------------------------------------

  async zRem(key: string, members: string | string[]): Promise<number> {
    const list = Array.isArray(members) ? members : [members];
    return await this.client.zRem(key, list);
  }

  async zRemRangeByRank(key: string, start: number, stop: number): Promise<number> {
    return await this.client.zRemRangeByRank(key, start, stop);
  }

  async zRemRangeByScore(key: string, min: number | string, max: number | string): Promise<number> {
    return await this.client.zRemRangeByScore(key, min, max);
  }

  async zPopMin(key: string, count?: number): Promise<{ value: string; score: number }[]> {
    const res = await this.client.zPopMinCount(key, count || 1);
    return res;
  }

  async zPopMax(key: string, count?: number): Promise<{ value: string; score: number }[]> {
    const res = await this.client.zPopMaxCount(key, count || 1);
    return res;
  }

  // --------------------------------------------------------------------------
  // 4. Encodings
  // --------------------------------------------------------------------------

  async getObjectEncoding(key: string): Promise<string | null> {
    try {
      const res = await this.client.sendCommand(["OBJECT", "ENCODING", key]);
      return res as string;
    } catch {
      return null;
    }
  }

  // --------------------------------------------------------------------------
  // 5. SDE-2 System Design Patterns
  // --------------------------------------------------------------------------

  /**
   * Pattern A: Top-K Gaming Leaderboard
   * Returns top N players with ranks (1-indexed) and scores.
   */
  async getTopKLeaderboard(key: string, k: number): Promise<LeaderboardEntry[]> {
    const topMembers = await this.client.zRangeWithScores(key, 0, k - 1, { REV: true });
    return topMembers.map((entry, idx) => ({
      rank: idx + 1,
      member: entry.value,
      score: entry.score,
    }));
  }

  /**
   * Get player's current leaderboard rank (1-indexed) and score.
   */
  async getPlayerRank(key: string, player: string): Promise<{ rank: number; score: number } | null> {
    const revRank = await this.client.zRevRank(key, player);
    if (revRank === null || revRank === undefined) return null;

    const score = await this.client.zScore(key, player);
    return {
      rank: revRank + 1,
      score: score || 0,
    };
  }

  /**
   * Pattern B: Sliding Window Log Rate Limiter
   * Uses timestamps as ZSet scores to strictly enforce rolling window limits.
   */
  async checkSlidingWindowRateLimit(
    rateLimitKey: string,
    limit: number,
    windowMs: number
  ): Promise<SlidingWindowRateLimitResult> {
    const now = Date.now();
    const windowStart = now - windowMs;
    const requestId = `${now}:${Math.random().toString(36).substring(2, 7)}`;

    // Atomic Lua script:
    // 1. Remove elements outside rolling window (score < windowStart)
    // 2. Count remaining requests in window (ZCARD)
    // 3. If within limit, add current request timestamp (ZADD)
    // 4. Refresh TTL to auto-expire idle keys
    const luaScript = `
      redis.call("ZREMRANGEBYSCORE", KEYS[1], "-inf", ARGV[1])
      local current = redis.call("ZCARD", KEYS[1])
      if current < tonumber(ARGV[2]) then
        redis.call("ZADD", KEYS[1], ARGV[3], ARGV[4])
        redis.call("PEXPIRE", KEYS[1], ARGV[5])
        return { 1, current + 1 }
      else
        return { 0, current }
      end
    `;

    const res = (await this.client.eval(luaScript, {
      keys: [rateLimitKey],
      arguments: [
        String(windowStart),
        String(limit),
        String(now),
        requestId,
        String(windowMs * 2), // TTL safely exceeds window
      ],
    })) as [number, number];

    const allowed = res[0] === 1;
    const currentCount = res[1];

    return {
      allowed,
      currentCount,
      remaining: Math.max(0, limit - currentCount),
      resetTimeMs: now + windowMs,
    };
  }
}

