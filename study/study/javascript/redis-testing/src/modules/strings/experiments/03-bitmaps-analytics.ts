/**
 * Experiment 03: Redis Bitmaps for Large-Scale Analytics (SDE-2 System Design)
 * 
 * Demonstrates:
 * 1. String-backed Bitmaps for Daily Active Users (DAU).
 * 2. High-performance population count using BITCOUNT (Hamming weight).
 * 3. Cohort retention analysis using bitwise BITOP AND (Day 1 ∩ Day 2).
 * 4. Total unique reach calculation using bitwise BITOP OR (Day 1 ∪ Day 2).
 * 5. Memory efficiency calculations: 100 million users in ~12 MB of RAM.
 */
import { getRedisClient, closeRedisClient } from "../../../client/redis-client.js";
import { createTestNamespace } from "../../../client/key-manager.js";

async function runBitmapsExperiment() {
  const client = await getRedisClient();
  const ns = createTestNamespace("exp-bitmaps");

  console.log("\n=========================================================================");
  console.log("📊 Experiment 03: Redis Bitmaps for Real-Time Analytics (SDE-2 System Design)");
  console.log("=========================================================================\n");

  const dauMonday = ns.key("dau:2026-11-09");
  const dauTuesday = ns.key("dau:2026-11-10");
  const retainedKey = ns.key("dau:retained:mon-tue");
  const uniqueReachKey = ns.key("dau:unique-reach:mon-tue");

  // 1. Simulating Active Users on Monday
  console.log("1️⃣  Simulating User Logins on Monday...");
  // Active user IDs on Monday
  const mondayUsers = [1, 5, 12, 42, 88, 105, 999, 100_000];
  for (const userId of mondayUsers) {
    await client.setBit(dauMonday, userId, 1);
  }
  const monCount = await client.bitCount(dauMonday);
  console.log(`   Monday Active Users (BITCOUNT): ${monCount}`);

  // 2. Simulating Active Users on Tuesday
  console.log("\n2️⃣  Simulating User Logins on Tuesday...");
  // Active user IDs on Tuesday (42, 88, 100_000 are returning users; others are new)
  const tuesdayUsers = [42, 88, 100_000, 204, 305, 500];
  for (const userId of tuesdayUsers) {
    await client.setBit(dauTuesday, userId, 1);
  }
  const tueCount = await client.bitCount(dauTuesday);
  console.log(`   Tuesday Active Users (BITCOUNT): ${tueCount}`);

  // 3. User Retention via BITOP AND (Intersection)
  console.log("\n3️⃣  Calculating 2-Day User Retention using BITOP AND...");
  await client.bitOp("AND", retainedKey, [dauMonday, dauTuesday]);
  const retainedCount = await client.bitCount(retainedKey);
  console.log(`   Users active on BOTH Monday and Tuesday: ${retainedCount}`);

  const retentionRate = ((retainedCount / monCount) * 100).toFixed(2);
  console.log(`   Day-1 Retention Rate: ${retentionRate}%`);

  // Verify retained user IDs
  console.log("   Verifying retained user IDs via GETBIT:");
  for (const uid of [42, 88, 100_000, 1, 5]) {
    const isRetained = await client.getBit(retainedKey, uid);
    console.log(`     - User ${uid.toString().padEnd(7)}: ${isRetained === 1 ? "✅ Retained" : "❌ Not active on Tue"}`);
  }

  // 4. Total Unique Reach via BITOP OR (Union)
  console.log("\n4️⃣  Calculating Total Multi-Day Reach using BITOP OR...");
  await client.bitOp("OR", uniqueReachKey, [dauMonday, dauTuesday]);
  const uniqueTotal = await client.bitCount(uniqueReachKey);
  console.log(`   Total Unique Users reached across Mon & Tue: ${uniqueTotal}`);

  // 5. Memory Footprint Analysis (SDE-2 Interview Math)
  console.log("\n5️⃣  System Design Interview Math: Bitmap Memory Footprint");
  const memMonday = await client.sendCommand(["MEMORY", "USAGE", dauMonday]);
  console.log(`   Actual Redis RAM used for Monday bitmap (max offset 100,000): ${memMonday} bytes (~${(Number(memMonday) / 1024).toFixed(1)} KB)`);

  console.log("\n   🧮 Theoretical Scaling to 100,000,000 Active Users:");
  console.log("      - 1 bit per user ID");
  console.log("      - 100,000,000 bits / 8 bits per byte = 12,500,000 bytes");
  console.log("      - 12,500,000 / 1024 / 1024 = 11.92 MB of RAM per day!");
  console.log("      - Comparing with storing 100M user UUIDs in a Set: ~3.2 GB of RAM!");
  console.log("      - Bitmap provides a ~270x memory reduction for integer-mapped IDs!");

  // Clean up
  await ns.cleanup(client);
  console.log("\n🧹 Cleaned up experiment keys.");
  console.log("=========================================================================\n");
}

runBitmapsExperiment()
  .then(async () => {
    await closeRedisClient();
    process.exit(0);
  })
  .catch(async (err) => {
    console.error("Experiment failed:", err);
    await closeRedisClient();
    process.exit(1);
  });

