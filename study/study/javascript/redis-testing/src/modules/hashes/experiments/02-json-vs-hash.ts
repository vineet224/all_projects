/**
 * Experiment: JSON String vs Redis Hash Modeling
 * 
 * SDE-2 System Design Comparison:
 * When storing structured domain entities (e.g. User Profile, Session, Shopping Cart),
 * should you serialize to JSON String or store as Redis Hash?
 */
import { getRedisClient, closeRedisClient } from "../../../client/redis-client.js";
import { createTestNamespace } from "../../../client/key-manager.js";

async function runJsonVsHashExperiment() {
  const client = await getRedisClient();
  const ns = createTestNamespace("exp-json-hash");

  console.log("\n=========================================================================");
  console.log("⚖️  Hashes Experiment: JSON String vs Redis Hash (SDE-2 Architecture)");
  console.log("=========================================================================\n");

  const jsonKey = ns.key("user:json");
  const hashKey = ns.key("user:hash");

  const profile = {
    id: "usr_500",
    name: "Sarah Connor",
    email: "sarah@resistance.org",
    loginCount: 14,
    lastSeen: Date.now(),
    preferences: "theme:dark|notify:true|lang:en",
  };

  // 1. Store as JSON String
  await client.set(jsonKey, JSON.stringify(profile));
  const jsonMem = await client.sendCommand(["MEMORY", "USAGE", jsonKey]);

  // 2. Store as Redis Hash
  await client.hSet(hashKey, {
    id: profile.id,
    name: profile.name,
    email: profile.email,
    loginCount: String(profile.loginCount),
    lastSeen: String(profile.lastSeen),
    preferences: profile.preferences,
  });
  const hashMem = await client.sendCommand(["MEMORY", "USAGE", hashKey]);

  console.log(`📦 Memory Comparison:`);
  console.log(`   - JSON String: ${jsonMem} bytes`);
  console.log(`   - Redis Hash:  ${hashMem} bytes\n`);

  console.log(`⚡ Scenario 1: Partial Field Read (e.g., read ONLY email)`);
  // Hash can read single field without pulling entire payload over network
  const emailFromHash = await client.hGet(hashKey, "email");
  console.log(`   - Hash HGET: Fetches ONLY 20 bytes over network: "${emailFromHash}"`);
  console.log(`   - JSON GET:  Must fetch all ${JSON.stringify(profile).length} bytes, then CPU must JSON.parse()!\n`);

  console.log(`⚡ Scenario 2: Atomic Field Increment (e.g., loginCount + 1)`);
  // Hash can atomically increment field in O(1) time
  const newCount = await client.hIncrBy(hashKey, "loginCount", 1);
  console.log(`   - Hash HINCRBY: Atomic O(1) increment directly in Redis (new count: ${newCount})`);
  console.log(`   - JSON String: Requires Read-Modify-Write cycle (GET -> JSON.parse -> count++ -> JSON.stringify -> SET)`);
  console.log(`     ❌ Vulnerable to race conditions without optimistic locking (WATCH/MULTI)!\n`);

  console.log(`📋 SDE-2 Interview Decision Matrix:`);
  console.log(`   Use Redis Hash when:`);
  console.log(`     1. You need partial updates (e.g., user logged in, increment counter).`);
  console.log(`     2. You frequently read only a few attributes of a large entity.`);
  console.log(`     3. The fields are flat key-value pairs (or shallow nesting).`);
  console.log(`   Use JSON String when:`);
  console.log(`     1. The entity has deeply nested arrays/objects.`);
  console.log(`     2. You always read/write the entire payload together (e.g. read-only cache).`);
  console.log(`     3. You compress the JSON string (e.g., gzip/zstd) for massive RAM savings.`);

  await ns.cleanup(client);
  console.log("\n🧹 Cleaned up experiment keys.");
  console.log("=========================================================================\n");
}

runJsonVsHashExperiment()
  .then(async () => {
    await closeRedisClient();
    process.exit(0);
  })
  .catch(async (err) => {
    console.error("Experiment failed:", err);
    await closeRedisClient();
    process.exit(1);
  });

