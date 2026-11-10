import { createClient, RedisClientType } from "redis";
import dotenv from "dotenv";

dotenv.config();

export type AppRedisClient = RedisClientType;

let singletonClient: AppRedisClient | null = null;

export function getRedisUrl(): string {
  if (process.env.REDIS_URL) {
    return process.env.REDIS_URL;
  }
  const host = process.env.REDIS_HOST || "127.0.0.1";
  const port = process.env.REDIS_PORT || "6379";
  const password = process.env.REDIS_PASSWORD ? `:${process.env.REDIS_PASSWORD}@` : "";
  return `redis://${password}${host}:${port}`;
}

/**
 * Creates and connects a new, independent Redis client instance.
 * Useful for concurrent client tests (e.g. distributed locks, blocking commands, pub/sub).
 */
export async function createIsolatedClient(customUrl?: string): Promise<AppRedisClient> {
  const url = customUrl || getRedisUrl();
  const client = createClient({
    url,
    socket: {
      reconnectStrategy: (retries: number) => {
        if (retries > 10) {
          return new Error("Max Redis reconnection attempts reached");
        }
        return Math.min(retries * 50, 1000);
      },
    },
  }) as AppRedisClient;

  client.on("error", (err) => {
    // Avoid noisy unhandled errors in tests when disconnect is intentional
    if (!err.message?.includes("Connection is closed")) {
      console.error("[Redis Client Error]:", err.message);
    }
  });

  await client.connect();
  return client;
}

/**
 * Retrieves the shared singleton Redis client. Automatically connects on first call.
 */
export async function getRedisClient(): Promise<AppRedisClient> {
  if (singletonClient && singletonClient.isOpen) {
    return singletonClient;
  }

  singletonClient = await createIsolatedClient();
  return singletonClient;
}

/**
 * Gracefully disconnects the singleton client.
 */
export async function closeRedisClient(): Promise<void> {
  if (singletonClient && singletonClient.isOpen) {
    await singletonClient.quit();
    singletonClient = null;
  }
}

/**
 * Pings the Redis server and returns round-trip latency in milliseconds.
 */
export async function pingRedis(client?: AppRedisClient): Promise<{ alive: boolean; latencyMs: number }> {
  const activeClient = client || (await getRedisClient());
  const start = process.hrtime.bigint();
  const pong = await activeClient.ping();
  const latencyMs = Number(process.hrtime.bigint() - start) / 1_000_000;
  return {
    alive: pong === "PONG",
    latencyMs: Number(latencyMs.toFixed(3)),
  };
}

/**
 * Helper to execute raw Redis commands (e.g., OBJECT ENCODING, MEMORY USAGE, DEBUG, etc.)
 */
export async function executeRawCommand(
  client: AppRedisClient,
  command: string,
  ...args: string[]
): Promise<unknown> {
  return await client.sendCommand([command, ...args]);
}

