/**
 * Experiment 02: Distributed Locks with Redis Strings (SDE-2 System Design)
 * 
 * Demonstrates:
 * 1. The fatal flaw of SETNX + EXPIRE (non-atomic split call).
 * 2. Production atomic lock acquisition: SET lock_key token NX PX ttl_ms.
 * 3. Race condition & mutual exclusion among competing worker nodes.
 * 4. The "stolen lock release" trap (why naive DEL is dangerous).
 * 5. Safe atomic unlock via Lua script token verification.
 */
import { getRedisClient, closeRedisClient } from "../../../client/redis-client.js";
import { createTestNamespace } from "../../../client/key-manager.js";

async function runLocksExperiment() {
  const client = await getRedisClient();
  const ns = createTestNamespace("exp-locks");
  const lockKey = ns.key("resource:invoice:5001");

  console.log("\n=========================================================================");
  console.log("🔒 Experiment 02: Distributed Locking Deep Dive (SDE-2 System Design)");
  console.log("=========================================================================\n");

  // Step 1: Atomic Acquisition vs Naive Acquisition
  console.log("1️⃣  Comparing Lock Acquisition Primitives:");
  console.log("   ❌ Anti-pattern: SETNX key token followed by EXPIRE key ttl");
  console.log("      Risk: If the worker process crashes after SETNX, EXPIRE never executes.");
  console.log("      Result: Key remains persistent forever, causing a permanent system DEADLOCK!\n");

  console.log("   ✅ Production pattern: SET key token NX PX ttl_ms");
  console.log("      Redis executes this as a single atomic primitive in O(1) time.\n");

  const worker1Token = "worker-node-1_uuid_" + Math.random().toString(36).substring(2, 8);
  const worker2Token = "worker-node-2_uuid_" + Math.random().toString(36).substring(2, 8);

  // Worker 1 acquires lock for 1000ms
  console.log(`2️⃣  Worker 1 acquiring lock on "${lockKey}" with TTL 1000ms...`);
  const w1Acquired = await client.set(lockKey, worker1Token, { NX: true, PX: 1000 });
  console.log(`   Worker 1 acquisition result: ${w1Acquired ? "SUCCESS (Lock Acquired)" : "FAILED"}`);

  // Worker 2 attempts to acquire the same lock concurrently
  console.log(`\n3️⃣  Worker 2 attempting to acquire the same lock concurrently...`);
  const w2Acquired = await client.set(lockKey, worker2Token, { NX: true, PX: 1000 });
  console.log(`   Worker 2 acquisition result: ${w2Acquired ? "SUCCESS" : "BLOCKED (Mutual Exclusion Maintained!)"}`);

  // Step 4: The Stolen Lock Trap demonstration
  console.log(`\n4️⃣  Demonstrating the 'Stolen Lock Release' Bug:`);
  console.log("   Suppose Worker 1 experiences a GC pause or slow network and exceeds TTL.");
  console.log("   Waiting 1100ms for Worker 1's lock lease to expire naturally in Redis...");
  await new Promise((r) => setTimeout(r, 1100));

  // Now Worker 2 tries again and succeeds
  const w2Retry = await client.set(lockKey, worker2Token, { NX: true, PX: 2000 });
  console.log(`   Worker 2 retry after expiration: ${w2Retry ? "SUCCESS (Worker 2 now holds the lock)" : "FAILED"}`);

  // Now Worker 1 wakes up from pause and attempts to release lock!
  console.log(`   Worker 1 wakes up and attempts naive DEL "${lockKey}"...`);
  console.log(`   ❌ IF Worker 1 used 'DEL ${lockKey}', it would delete Worker 2's active lock!`);

  // Safe Release via Lua Script
  console.log(`\n5️⃣  Executing Atomic Safe Release via Lua Script:`);
  const luaScript = `
    if redis.call("get", KEYS[1]) == ARGV[1] then
      return redis.call("del", KEYS[1])
    else
      return 0
    end
  `;

  // Worker 1 tries to release with its old token
  const w1ReleaseResult = await client.eval(luaScript, {
    keys: [lockKey],
    arguments: [worker1Token],
  });
  console.log(`   Worker 1 release attempt with old token: ${w1ReleaseResult === 1 ? "RELEASED" : "REJECTED (Token Mismatch! Protected Worker 2!)"}`);

  // Worker 2 releases with its valid token
  const w2ReleaseResult = await client.eval(luaScript, {
    keys: [lockKey],
    arguments: [worker2Token],
  });
  console.log(`   Worker 2 release attempt with valid token: ${w2ReleaseResult === 1 ? "RELEASED (Success)" : "REJECTED"}`);

  // Clean up
  await ns.cleanup(client);
  console.log("\n🧹 Cleaned up experiment keys.");
  console.log("=========================================================================\n");
}

runLocksExperiment()
  .then(async () => {
    await closeRedisClient();
    process.exit(0);
  })
  .catch(async (err) => {
    console.error("Experiment failed:", err);
    await closeRedisClient();
    process.exit(1);
  });

