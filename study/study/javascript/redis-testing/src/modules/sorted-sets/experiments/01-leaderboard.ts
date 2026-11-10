/**
 * Experiment: Real-Time Gaming Leaderboard with Redis Sorted Sets (ZSet)
 * 
 * SDE-2 System Design Deep Dive:
 * Demonstrates:
 * 1. ZADD with score updating.
 * 2. High-speed O(log N + K) Top-K query via ZREVRANGE.
 * 3. Individual player rank & score lookup in O(log N) via ZREVRANK & ZSCORE.
 * 4. Dynamic score incrementing via ZINCRBY.
 */
import { getRedisClient, closeRedisClient } from "../../../client/redis-client.js";
import { createTestNamespace } from "../../../client/key-manager.js";

async function runLeaderboardExperiment() {
  const client = await getRedisClient();
  const ns = createTestNamespace("exp-zset-lb");

  console.log("\n=========================================================================");
  console.log("🏆 Sorted Sets Experiment: Real-Time Gaming Leaderboard (SDE-2 Design)");
  console.log("=========================================================================\n");

  const lbKey = ns.key("game:leaderboard:season1");

  // 1. Populate initial player scores
  const players = [
    { value: "Valkyrie", score: 9800 },
    { value: "Shadow", score: 14200 },
    { value: "Phoenix", score: 11500 },
    { value: "Apex", score: 8750 },
    { value: "Titan", score: 16400 },
    { value: "Nova", score: 12100 },
  ];

  await client.zAdd(lbKey, players);
  console.log(`1️⃣  Populated ${players.length} players into leaderboard.`);

  // 2. Fetch Top 3 players
  const top3 = await client.zRangeWithScores(lbKey, 0, 2, { REV: true });
  console.log("\n2️⃣  Global Top 3 (ZREVRANGE 0 2 WITHSCORES):");
  top3.forEach((entry, idx) => {
    console.log(`   Rank #${idx + 1}: ${entry.value.padEnd(10)} | Score: ${entry.score}`);
  });

  // 3. Player lookup: Shadow's current rank
  const shadowRank = await client.zRevRank(lbKey, "Shadow");
  const shadowScore = await client.zScore(lbKey, "Shadow");
  console.log(`\n3️⃣  Player Lookup ("Shadow"):`);
  console.log(`   Rank: #${(shadowRank ?? 0) + 1} of ${await client.zCard(lbKey)}`);
  console.log(`   Score: ${shadowScore}`);

  // 4. In-game score boost: Phoenix gets +5,000 points (ZINCRBY)
  console.log("\n4️⃣  Phoenix completes legendary quest (+5,000 pts via ZINCRBY)...");
  const newPhoenixScore = await client.zIncrBy(lbKey, 5000, "Phoenix");
  const newPhoenixRank = await client.zRevRank(lbKey, "Phoenix");
  console.log(`   Phoenix New Score: ${newPhoenixScore} (Jumped to Rank #${(newPhoenixRank ?? 0) + 1}!)`);

  // 5. Updated Top 3
  const updatedTop3 = await client.zRangeWithScores(lbKey, 0, 2, { REV: true });
  console.log("\n5️⃣  Updated Global Top 3:");
  updatedTop3.forEach((entry, idx) => {
    console.log(`   Rank #${idx + 1}: ${entry.value.padEnd(10)} | Score: ${entry.score}`);
  });

  console.log("\n💡 SDE-2 Interview Architecture Insight:");
  console.log("   - Redis ZSet uses a SkipList + Hash Table in C.");
  console.log("   - Hash Table provides O(1) score lookup (ZSCORE).");
  console.log("   - SkipList provides O(log N) rank lookup (ZRANK) and O(log N + K) range scan (ZRANGE).");
  console.log("   - Scaling: In Redis Cluster, ZSet must reside on a single shard unless sharded by country or tier.");

  await ns.cleanup(client);
  console.log("\n🧹 Cleaned up experiment keys.");
  console.log("=========================================================================\n");
}

runLeaderboardExperiment()
  .then(async () => {
    await closeRedisClient();
    process.exit(0);
  })
  .catch(async (err) => {
    console.error("Experiment failed:", err);
    await closeRedisClient();
    process.exit(1);
  });

