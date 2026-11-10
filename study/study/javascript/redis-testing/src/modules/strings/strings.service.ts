import { AppRedisClient } from "../../client/redis-client.js";

export interface RateLimitResult {
  allowed: boolean;
  currentCount: number;
  remaining: number;
  resetTimeMs: number;
}

export class StringsService {
  constructor(private client: AppRedisClient) {}

  // --------------------------------------------------------------------------
  // 1. Basic Read & Write
  // --------------------------------------------------------------------------

  async set(
    key: string,
    value: string | number,
    options?: {
      EX?: number;
      PX?: number;
      EXAT?: number;
      PXAT?: number;
      KEEPTTL?: boolean;
      NX?: boolean;
      XX?: boolean;
      GET?: boolean;
    }
  ): Promise<string | null> {
    const valStr = String(value);
    // In node-redis, client.set returns string ("OK" or previous value if GET) or null
    return (await this.client.set(key, valStr, options as any)) as string | null;
  }

  async get(key: string): Promise<string | null> {
    return await this.client.get(key);
  }

  async mSet(record: Record<string, string | number>): Promise<string> {
    const stringRecord: Record<string, string> = {};
    for (const [k, v] of Object.entries(record)) {
      stringRecord[k] = String(v);
    }
    return await this.client.mSet(stringRecord);
  }

  async mGet(keys: string[]): Promise<(string | null)[]> {
    if (keys.length === 0) return [];
    return await this.client.mGet(keys);
  }

  /**
   * Sets value and returns old value atomically.
   * Prefer `set(key, value, { GET: true })` in Redis 6.2+ over deprecated `GETSET`.
   */
  async getSet(key: string, value: string): Promise<string | null> {
    return await this.client.getSet(key, value);
  }

  // --------------------------------------------------------------------------
  // 2. Conditionals & Atomic Expirations
  // --------------------------------------------------------------------------

  async setNx(key: string, value: string | number): Promise<boolean> {
    const res = await this.client.setNX(key, String(value));
    return res;
  }

  /**
   * Atomic multi-key set if NOT exists. All or nothing.
   * Returns true if all keys were set, false if none were set.
   */
  async mSetNx(record: Record<string, string | number>): Promise<boolean> {
    const stringRecord: Record<string, string> = {};
    for (const [k, v] of Object.entries(record)) {
      stringRecord[k] = String(v);
    }
    return await this.client.mSetNX(stringRecord);
  }

  /**
   * Atomically gets the value and deletes the key (Redis 6.2+).
   */
  async getDel(key: string): Promise<string | null> {
    return await this.client.getDel(key);
  }

  /**
   * Atomically gets the value and updates/clears expiration (Redis 6.2+).
   */
  async getEx(
    key: string,
    options?: {
      EX?: number;
      PX?: number;
      EXAT?: number;
      PXAT?: number;
      PERSIST?: boolean;
    }
  ): Promise<string | null> {
    return await this.client.getEx(key, options as any);
  }

  // --------------------------------------------------------------------------
  // 3. Numeric & Counter Operations
  // --------------------------------------------------------------------------

  async incr(key: string): Promise<number> {
    return await this.client.incr(key);
  }

  async decr(key: string): Promise<number> {
    return await this.client.decr(key);
  }

  async incrBy(key: string, amount: number): Promise<number> {
    return await this.client.incrBy(key, amount);
  }

  async decrBy(key: string, amount: number): Promise<number> {
    return await this.client.decrBy(key, amount);
  }

  async incrByFloat(key: string, amount: number): Promise<number> {
    const res = await this.client.incrByFloat(key, amount);
    return typeof res === "number" ? res : parseFloat(String(res));
  }

  // --------------------------------------------------------------------------
  // 4. Substring & Offset Operations
  // --------------------------------------------------------------------------

  async append(key: string, value: string): Promise<number> {
    return await this.client.append(key, value);
  }

  async strLen(key: string): Promise<number> {
    return await this.client.strLen(key);
  }

  async setRange(key: string, offset: number, value: string): Promise<number> {
    return await this.client.setRange(key, offset, value);
  }

  async getRange(key: string, start: number, end: number): Promise<string> {
    return await this.client.getRange(key, start, end);
  }

  // --------------------------------------------------------------------------
  // 5. Bitmaps / Bitwise Operations
  // --------------------------------------------------------------------------

  async setBit(key: string, offset: number, value: 0 | 1): Promise<number> {
    return await this.client.setBit(key, offset, value);
  }

  async getBit(key: string, offset: number): Promise<number> {
    return await this.client.getBit(key, offset);
  }

  async bitCount(
    key: string,
    start?: number,
    end?: number,
    mode?: "BYTE" | "BIT"
  ): Promise<number> {
    if (start !== undefined && end !== undefined) {
      return await this.client.bitCount(key, { start, end, mode });
    }
    return await this.client.bitCount(key);
  }

  async bitPos(key: string, bit: 0 | 1, start?: number, end?: number): Promise<number> {
    if (start !== undefined && end !== undefined) {
      return await this.client.bitPos(key, bit, start, end);
    }
    if (start !== undefined) {
      return await this.client.bitPos(key, bit, start);
    }
    return await this.client.bitPos(key, bit);
  }

  async bitOp(
    operation: "AND" | "OR" | "XOR" | "NOT",
    destKey: string,
    srcKeys: string | string[]
  ): Promise<number> {
    const keys = Array.isArray(srcKeys) ? srcKeys : [srcKeys];
    return await this.client.bitOp(operation, destKey, keys);
  }

  // --------------------------------------------------------------------------
  // 6. Memory & Encodings (Internal Inspection)
  // --------------------------------------------------------------------------

  async getObjectEncoding(key: string): Promise<string | null> {
    try {
      const res = await this.client.sendCommand(["OBJECT", "ENCODING", key]);
      return res as string;
    } catch {
      return null;
    }
  }

  async getMemoryUsage(key: string): Promise<number | null> {
    try {
      const res = await this.client.sendCommand(["MEMORY", "USAGE", key]);
      return typeof res === "number" ? res : null;
    } catch {
      return null;
    }
  }

  // --------------------------------------------------------------------------
  // 7. SDE-2 System Design Patterns
  // --------------------------------------------------------------------------

  /**
   * Acquire a distributed lock atomically using `SET NX PX`.
   * Returns true if lock was acquired.
   */
  async acquireLock(lockKey: string, ownerId: string, ttlMs: number): Promise<boolean> {
    const result = await this.client.set(lockKey, ownerId, {
      PX: ttlMs,
      NX: true,
    });
    return result === "OK";
  }

  /**
   * Releases distributed lock safely using an atomic Lua script.
   * Ensures a client only releases the lock if it still owns it (prevents releasing a stolen lock after TTL expiration).
   */
  async releaseLock(lockKey: string, ownerId: string): Promise<boolean> {
    const luaScript = `
      if redis.call("get", KEYS[1]) == ARGV[1] then
        return redis.call("del", KEYS[1])
      else
        return 0
      end
    `;
    const res = await this.client.eval(luaScript, {
      keys: [lockKey],
      arguments: [ownerId],
    });
    return res === 1;
  }

  /**
   * Fixed-window Rate Limiter pattern.
   * Atomically increments the counter and sets the TTL on the first request of the window.
   */
  async checkRateLimit(
    rateLimitKey: string,
    limit: number,
    windowSeconds: number
  ): Promise<RateLimitResult> {
    // Lua script to make increment and conditional expiration strictly atomic
    const luaScript = `
      local current = redis.call("INCR", KEYS[1])
      if current == 1 then
        redis.call("EXPIRE", KEYS[1], ARGV[1])
      end
      local ttl = redis.call("PTTL", KEYS[1])
      return {current, ttl}
    `;

    const res = (await this.client.eval(luaScript, {
      keys: [rateLimitKey],
      arguments: [String(windowSeconds)],
    })) as [number, number];

    const currentCount = res[0];
    const ttlMs = res[1];
    const allowed = currentCount <= limit;
    const remaining = Math.max(0, limit - currentCount);
    const resetTimeMs = Date.now() + ttlMs;

    return {
      allowed,
      currentCount,
      remaining,
      resetTimeMs,
    };
  }
}
