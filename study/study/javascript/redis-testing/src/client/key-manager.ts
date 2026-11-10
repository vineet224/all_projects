import crypto from "crypto";
import { AppRedisClient } from "./redis-client.js";

export interface TestNamespace {
  readonly prefix: string;
  readonly runId: string;
  key(name: string): string;
  cleanup(client: AppRedisClient): Promise<number>;
}

/**
 * Creates an isolated keyspace namespace for a test suite or experiment run.
 * Guarantees zero key collisions between parallel or repeated test executions.
 */
export function createTestNamespace(moduleName: string): TestNamespace {
  const basePrefix = process.env.TEST_KEY_PREFIX || "lab";
  const runId = crypto.randomBytes(4).toString("hex");
  const prefix = `${basePrefix}:${moduleName}:${runId}`;

  return {
    prefix,
    runId,
    key(name: string): string {
      return `${prefix}:${name}`;
    },
    async cleanup(client: AppRedisClient): Promise<number> {
      let cursor = 0;
      let totalDeleted = 0;
      const matchPattern = `${prefix}:*`;

      do {
        const reply = await client.scan(cursor, {
          MATCH: matchPattern,
          COUNT: 100,
        });

        cursor = reply.cursor;
        const keys = reply.keys;

        if (keys.length > 0) {
          // UNLINK is non-blocking (async memory reclaim), falling back to DEL if unsupported
          try {
            const deleted = await client.unlink(keys);
            totalDeleted += deleted;
          } catch {
            const deleted = await client.del(keys);
            totalDeleted += deleted;
          }
        }
      } while (cursor !== 0);

      return totalDeleted;
    },
  };
}

