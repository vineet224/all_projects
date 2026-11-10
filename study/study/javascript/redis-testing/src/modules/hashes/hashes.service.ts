import { AppRedisClient } from "../../client/redis-client.js";

export interface CartItem {
  id: string;
  name: string;
  price: number;
  quantity: number;
}

export interface UserProfile {
  id: string;
  username: string;
  email: string;
  loginCount: number;
  lastLoginAt: number;
  tier: "free" | "pro" | "enterprise";
}

export class HashesService {
  constructor(private client: AppRedisClient) {}

  // --------------------------------------------------------------------------
  // 1. Basic Field Read & Write
  // --------------------------------------------------------------------------

  /**
   * Sets single field or multiple fields in a Hash.
   * Returns the number of fields that were newly added (not updated).
   */
  async hSet(key: string, field: string, value: string | number): Promise<number> {
    return await this.client.hSet(key, field, String(value));
  }

  async hSetMultiple(key: string, record: Record<string, string | number>): Promise<number> {
    const stringRecord: Record<string, string> = {};
    for (const [k, v] of Object.entries(record)) {
      stringRecord[k] = String(v);
    }
    return await this.client.hSet(key, stringRecord);
  }

  async hGet(key: string, field: string): Promise<string | null> {
    const val = await this.client.hGet(key, field);
    return val ?? null;
  }

  async hmGet(key: string, fields: string[]): Promise<(string | null)[]> {
    if (fields.length === 0) return [];
    const values = await this.client.hmGet(key, fields);
    return values.map((v) => (v !== undefined ? v : null));
  }

  async hGetAll(key: string): Promise<Record<string, string>> {
    return await this.client.hGetAll(key);
  }

  /**
   * Sets field only if it does NOT already exist.
   * Returns true if field was set, false if field already existed.
   */
  async hSetNx(key: string, field: string, value: string | number): Promise<boolean> {
    return await this.client.hSetNX(key, field, String(value));
  }

  // --------------------------------------------------------------------------
  // 2. Deletion, Existence & Length
  // --------------------------------------------------------------------------

  async hDel(key: string, fields: string | string[]): Promise<number> {
    const fieldList = Array.isArray(fields) ? fields : [fields];
    return await this.client.hDel(key, fieldList);
  }

  async hExists(key: string, field: string): Promise<boolean> {
    return await this.client.hExists(key, field);
  }

  async hLen(key: string): Promise<number> {
    return await this.client.hLen(key);
  }

  async hStrLen(key: string, field: string): Promise<number> {
    return await this.client.hStrLen(key, field);
  }

  // --------------------------------------------------------------------------
  // 3. Inspection & Iteration
  // --------------------------------------------------------------------------

  async hKeys(key: string): Promise<string[]> {
    return await this.client.hKeys(key);
  }

  async hVals(key: string): Promise<string[]> {
    return await this.client.hVals(key);
  }

  /**
   * Non-blocking cursor-based iteration through hash fields.
   * Safe for production use on high-cardinality hashes where HGETALL would block the event loop.
   */
  async hScan(
    key: string,
    cursor: number,
    options?: { MATCH?: string; COUNT?: number }
  ): Promise<{ cursor: number; entries: { field: string; value: string }[] }> {
    const res = await this.client.hScan(key, cursor, options);
    return {
      cursor: res.cursor,
      entries: (res.tuples || []).map((e) => ({ field: String(e.field), value: String(e.value) })),
    };
  }

  /**
   * Returns random fields from a hash.
   */
  async hRandField(key: string): Promise<string | null> {
    const res = await this.client.hRandField(key);
    return res ?? null;
  }

  async hRandFieldCount(key: string, count: number): Promise<string[]> {
    const res = await this.client.hRandFieldCount(key, count);
    return res as string[];
  }

  // --------------------------------------------------------------------------
  // 4. Numeric & Counter Operations
  // --------------------------------------------------------------------------

  async hIncrBy(key: string, field: string, increment: number): Promise<number> {
    return await this.client.hIncrBy(key, field, increment);
  }

  async hIncrByFloat(key: string, field: string, increment: number): Promise<number> {
    const res = await this.client.hIncrByFloat(key, field, increment);
    return typeof res === "number" ? res : parseFloat(String(res));
  }

  // --------------------------------------------------------------------------
  // 5. Field-Level Expiration (Redis 7.4+ HEXPIRE)
  // --------------------------------------------------------------------------

  /**
   * Sets TTL on specific fields inside a Hash without expiring the parent key.
   * Returns an array of integer reply codes for each field:
   *  1: Expiration time was set or updated.
   *  2: Specified condition (NX/XX/GT/LT) was not met.
   *  0: Field does not exist in hash.
   * -2: Key does not exist.
   */
  async hExpire(
    key: string,
    seconds: number,
    fields: string | string[],
    condition?: "NX" | "XX" | "GT" | "LT"
  ): Promise<number[]> {
    const fieldList = Array.isArray(fields) ? fields : [fields];
    const args = ["HEXPIRE", key, String(seconds)];
    if (condition) {
      args.push(condition);
    }
    args.push("FIELDS", String(fieldList.length), ...fieldList);

    const reply = (await this.client.sendCommand(args)) as number[];
    return reply;
  }

  /**
   * Retrieves TTL in seconds for specific fields inside a Hash.
   * Return codes:
   *  >= 0: TTL remaining in seconds.
   *  -1: Field exists with no expiration.
   *  -2: Field does not exist or key does not exist.
   */
  async hTtl(key: string, fields: string | string[]): Promise<number[]> {
    const fieldList = Array.isArray(fields) ? fields : [fields];
    const args = ["HTTL", key, "FIELDS", String(fieldList.length), ...fieldList];
    const reply = (await this.client.sendCommand(args)) as number[];
    return reply;
  }

  /**
   * Removes expiration on specific fields inside a Hash, making them persistent.
   *  1: Expiration removed.
   * -1: Field had no expiration.
   * -2: Field does not exist.
   */
  async hPersist(key: string, fields: string | string[]): Promise<number[]> {
    const fieldList = Array.isArray(fields) ? fields : [fields];
    const args = ["HPERSIST", key, "FIELDS", String(fieldList.length), ...fieldList];
    const reply = (await this.client.sendCommand(args)) as number[];
    return reply;
  }

  // --------------------------------------------------------------------------
  // 6. Memory Encodings & Internals
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
   * Pattern A: User Profile & Session Store
   * Partial field updates without deserializing entire JSON payloads.
   */
  async saveUserProfile(profile: UserProfile): Promise<void> {
    const key = `user:profile:${profile.id}`;
    await this.client.hSet(key, {
      id: profile.id,
      username: profile.username,
      email: profile.email,
      loginCount: String(profile.loginCount),
      lastLoginAt: String(profile.lastLoginAt),
      tier: profile.tier,
    });
  }

  async getUserProfile(userId: string): Promise<UserProfile | null> {
    const key = `user:profile:${userId}`;
    const raw = await this.client.hGetAll(key);
    if (!raw || Object.keys(raw).length === 0) return null;

    return {
      id: raw.id,
      username: raw.username,
      email: raw.email,
      loginCount: parseInt(raw.loginCount || "0", 10),
      lastLoginAt: parseInt(raw.lastLoginAt || "0", 10),
      tier: (raw.tier as any) || "free",
    };
  }

  /**
   * Atomically records a login event: increments counter and updates last login timestamp.
   */
  async recordUserLogin(userId: string): Promise<{ loginCount: number; lastLoginAt: number }> {
    const key = `user:profile:${userId}`;
    const timestamp = Date.now();

    const luaScript = `
      local count = redis.call("HINCRBY", KEYS[1], "loginCount", 1)
      redis.call("HSET", KEYS[1], "lastLoginAt", ARGV[1])
      return { count, ARGV[1] }
    `;

    const res = (await this.client.eval(luaScript, {
      keys: [key],
      arguments: [String(timestamp)],
    })) as [number, string];

    return {
      loginCount: res[0],
      lastLoginAt: parseInt(res[1], 10),
    };
  }

  /**
   * Pattern B: E-Commerce Shopping Cart
   * Manages cart items, quantities, and removals.
   */
  async updateCartItem(
    cartKey: string,
    itemId: string,
    quantityDelta: number,
    unitPrice: number
  ): Promise<{ quantity: number; removed: boolean }> {
    // Atomically increment quantity and adjust cart state
    const luaScript = `
      local current = redis.call("HINCRBY", KEYS[1], "item:" .. ARGV[1] .. ":qty", ARGV[2])
      if current <= 0 then
        redis.call("HDEL", KEYS[1], "item:" .. ARGV[1] .. ":qty")
        redis.call("HDEL", KEYS[1], "item:" .. ARGV[1] .. ":price")
        return { 0, 1 }
      else
        redis.call("HSET", KEYS[1], "item:" .. ARGV[1] .. ":price", ARGV[3])
        return { current, 0 }
      end
    `;

    const res = (await this.client.eval(luaScript, {
      keys: [cartKey],
      arguments: [itemId, String(quantityDelta), String(unitPrice)],
    })) as [number, number];

    return {
      quantity: res[0],
      removed: res[1] === 1,
    };
  }

  async getCartSummary(cartKey: string): Promise<{ items: { itemId: string; qty: number; price: number }[]; total: number }> {
    const raw = await this.client.hGetAll(cartKey);
    const itemsMap: Record<string, { qty: number; price: number }> = {};

    for (const [k, v] of Object.entries(raw)) {
      if (k.startsWith("item:") && k.endsWith(":qty")) {
        const id = k.split(":")[1];
        itemsMap[id] = itemsMap[id] || { qty: 0, price: 0 };
        itemsMap[id].qty = parseInt(v, 10);
      } else if (k.startsWith("item:") && k.endsWith(":price")) {
        const id = k.split(":")[1];
        itemsMap[id] = itemsMap[id] || { qty: 0, price: 0 };
        itemsMap[id].price = parseFloat(v);
      }
    }

    const items = Object.entries(itemsMap).map(([id, data]) => ({
      itemId: id,
      qty: data.qty,
      price: data.price,
    }));

    const total = items.reduce((sum, item) => sum + item.qty * item.price, 0);

    return { items, total: Number(total.toFixed(2)) };
  }
}
