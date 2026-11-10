/**
 * Experiment: Redis 7.4+ Field-Level Expiration (HEXPIRE & HTTL)
 * 
 * Demonstrates:
 * 1. Attaching TTLs to individual hash fields without expiring the parent key.
 * 2. Transient session tokens and OTPs stored alongside persistent user profiles.
 * 3. Inspecting remaining field TTL with HTTL.
 */
import { getRedisClient, closeRedisClient } from "../../../client/redis-client.js";
import { createTestNamespace } from "../../../client/key-manager.js";

async function runFieldExpirationExperiment() {
  const client = await getRedisClient();
  const ns = createTestNamespace("exp-hexpire");

  console.log("\n=========================================================================");
  console.log("⏳ Hashes Experiment: Redis 7.4+ Field-Level Expiration (HEXPIRE / HTTL)");
  console.log("=========================================================================\n");

  const userKey = ns.key("user:session:701");

  // Create hash with mixed persistent and transient fields
  await client.hSet(userKey, {
    userId: "usr_701",
    email: "dev@platform.io",
    mfaCode: "492018",
    sessionToken: "sess_temp_99a8b7",
  });

  console.log("1️⃣  Created Hash with 4 fields:");
  console.log("   - userId (persistent)");
  console.log("   - email (persistent)");
  console.log("   - mfaCode (transient - 2 second TTL)");
  console.log("   - sessionToken (transient - 4 second TTL)\n");

  // Set field-level TTLs via HEXPIRE
  await client.sendCommand(["HEXPIRE", userKey, "2", "FIELDS", "1", "mfaCode"]);
  await client.sendCommand(["HEXPIRE", userKey, "4", "FIELDS", "1", "sessionToken"]);

  // Inspect HTTL
  const initialTtls = (await client.sendCommand([
    "HTTL",
    userKey,
    "FIELDS",
    "4",
    "mfaCode",
    "sessionToken",
    "userId",
    "email",
  ])) as number[];

  console.log("2️⃣  Initial HTTL Inspection:");
  console.log(`   - mfaCode TTL:      ${initialTtls[0]}s`);
  console.log(`   - sessionToken TTL: ${initialTtls[1]}s`);
  console.log(`   - userId TTL:       ${initialTtls[2]} (persistent)`);
  console.log(`   - email TTL:        ${initialTtls[3]} (persistent)\n`);

  console.log("⏳ Waiting 2.2 seconds for mfaCode to expire naturally...");
  await new Promise((r) => setTimeout(r, 2200));

  const afterMfaAll = await client.hGetAll(userKey);
  console.log("3️⃣  Fields remaining after 2.2s:");
  console.log(afterMfaAll);
  console.log(`   mfaCode exists? ${"mfaCode" in afterMfaAll ? "YES" : "NO (Cleanly expired independently!)"}`);
  console.log(`   sessionToken exists? ${"sessionToken" in afterMfaAll ? "YES" : "NO"}\n`);

  console.log("⏳ Waiting 2.0 more seconds for sessionToken to expire...");
  await new Promise((r) => setTimeout(r, 2000));

  const finalAll = await client.hGetAll(userKey);
  console.log("4️⃣  Final fields remaining after 4.2s:");
  console.log(finalAll);
  console.log(`   Parent key exists? ${Boolean(await client.exists(userKey)) ? "YES (Never expired!)" : "NO"}`);

  console.log("\n💡 SDE-2 Interview Insight:");
  console.log("   - Prior to Redis 7.4, expiring a single attribute required storing it in a separate String key.");
  console.log("   - HEXPIRE eliminates key bloat and maintains relational coherence inside a single Hash data structure.");

  await ns.cleanup(client);
  console.log("\n🧹 Cleaned up experiment keys.");
  console.log("=========================================================================\n");
}

runFieldExpirationExperiment()
  .then(async () => {
    await closeRedisClient();
    process.exit(0);
  })
  .catch(async (err) => {
    console.error("Experiment failed:", err);
    await closeRedisClient();
    process.exit(1);
  });

