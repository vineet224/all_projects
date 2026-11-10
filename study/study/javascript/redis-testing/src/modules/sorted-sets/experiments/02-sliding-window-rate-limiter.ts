/**
 * Experiment: Sliding Window Rate Limiter using Redis Sorted Sets
 * 
 * SDE-2 System Design Deep Dive:
 * Why Sliding Window Log over Fixed Window Counter?
 * 
 * The Fixed Window flaw (Boundary Burst problem):
 * - If quota is 100 req/min, a user can make 100 requests at 00:59, and 100 requests at 01:00.
 * - This allows 200 requests within a 2-second burst window!
 * 
 * The Sliding Window Log solution:
 * - Each request timestamp is added as a member and score in a ZSet.
 * - Any timestamps older than (now - windowSize) are pruned via ZREMRANGEBYSCORE.
 * - ZCARD verifies current count in rolling window strictly.
 */
import { getRedisClient, closeRedisClient } from "../../../client/redis-client.js";
import { createTestNamespace } from "../../../client/key-manager.js";

async function runSlidingWindowExperiment() {
  const client = await getRedisClient();
  const ns = createTestNamespace("exp-zset-limiter");

  console.log("\n=========================================================================");
  console.log("⏱️  Sorted Sets Experiment: Sliding Window Log Rate Limiter (SDE-2)");
  console.log("=========================================================================\n");

  const limiterKey = ns.key("rate:user:4001");
  const limit = 4;
  const windowMs = 1000; // 4 requests allowed per 1-second rolling window

  const checkRateLimit = async (callerTag: string) => {
    const now = Date.now();
    const windowStart = now - windowMs;
    const reqId = `${now}:${Math.random().toString(36).substring(2, 6)}`;

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

    const res = (await client.eval(luaScript, {
      keys: [limiterKey],
      arguments: [String(windowStart), String(limit), String(now), reqId, String(windowMs * 2)],
    })) as [number, number];

    const allowed = res[0] === 1;
    const current = res[1];
    console.log(`   [${callerTag}] Status: ${allowed ? "✅ ALLOWED" : "❌ BLOCKED"} | Current Window Load: ${current}/${limit}`);
    return allowed;
  };

  console.log(`1️⃣  Simulating 5 rapid requests (Limit: ${limit} reqs / ${windowMs}ms)...`);
  for (let i = 1; i <= 5; i++) {
    await checkRateLimit(`Req #${i}`);
  }

  console.log(`\n⏳ Waiting 1100ms for rolling window to slide forward...`);
  await new Promise((r) => setTimeout(r, 1100));

  console.log(`\n2️⃣  Sending fresh request after window slide...`);
  await checkRateLimit("Req #6 (Post-slide)");

  console.log("\n💡 SDE-2 Tradeoff Analysis:");
  console.log("   - Accuracy: Perfect rolling window precision (zero boundary burst vulnerability).");
  console.log("   - Memory Cost: Each request timestamp is a ZSet member. For 100k req/sec, memory is high.");
  console.log("   - Mitigation: Sliding Window Counter (Token Bucket or Hybrid sliding counters) for massive traffic.");

  await ns.cleanup(client);
  console.log("\n🧹 Cleaned up experiment keys.");
  console.log("=========================================================================\n");
}

runSlidingWindowExperiment()
  .then(async () => {
    await closeRedisClient();
    process.exit(0);
  })
  .catch(async (err) => {
    console.error("Experiment failed:", err);
    await closeRedisClient();
    process.exit(1);
  });

