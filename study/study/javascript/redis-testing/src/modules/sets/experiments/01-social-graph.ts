/**
 * Experiment: Social Graph & Recommendation Engine with Redis Sets
 * 
 * Demonstrates:
 * 1. Modeling follow relationships using bidirectional sets.
 * 2. Real-time mutual follower intersection via SINTER (O(N * M)).
 * 3. Friend recommendation computation via SDIFF.
 * 4. SINTERCARD for zero-payload mutual count queries.
 */
import { getRedisClient, closeRedisClient } from "../../../client/redis-client.js";
import { createTestNamespace } from "../../../client/key-manager.js";

async function runSocialGraphExperiment() {
  const client = await getRedisClient();
  const ns = createTestNamespace("exp-sets");

  console.log("\n=========================================================================");
  console.log("👥 Sets Experiment: Social Graph & Recommendation Engine (SDE-2 Design)");
  console.log("=========================================================================\n");

  const aliceFollowing = ns.key("user:alice:following");
  const bobFollowing = ns.key("user:bob:following");

  // Setup: Alice follows tech leaders
  await client.sAdd(aliceFollowing, ["satya", "sundar", "sam", "elon"]);
  // Setup: Bob follows some shared and some different leaders
  await client.sAdd(bobFollowing, ["sundar", "sam", "jensen", "demis"]);

  console.log("1️⃣  Populated Followings:");
  console.log("   - Alice follows:", await client.sMembers(aliceFollowing));
  console.log("   - Bob follows:  ", await client.sMembers(bobFollowing));

  // Mutual followings via SINTER
  const mutual = await client.sInter([aliceFollowing, bobFollowing]);
  console.log("\n2️⃣  Mutual Followings (SINTER Alice ∩ Bob):", mutual);

  // Intersection count without fetching payload via SINTERCARD
  const mutualCount = await client.sInterCard([aliceFollowing, bobFollowing]);
  console.log(`   Mutual Following Count (SINTERCARD): ${mutualCount}`);

  // Recommendations for Alice: People Bob follows that Alice doesn't yet follow
  const recommendations = await client.sDiff([bobFollowing, aliceFollowing]);
  console.log("\n3️⃣  Follow Recommendations for Alice (SDIFF Bob - Alice):", recommendations);

  console.log("\n💡 SDE-2 Interview Insight:");
  console.log("   - Set algebra operations (SINTER, SUNION, SDIFF) run on the Redis server in C memory.");
  console.log("   - Time complexity of SINTER is O(N * M) where N is the smallest set. Redis automatically sorts inputs by cardinality first!");
  console.log("   - SINTERCARD (Redis 7.0+) avoids network deserialization when you only need to show 'X mutual connections' on a profile UI.");

  await ns.cleanup(client);
  console.log("\n🧹 Cleaned up experiment keys.");
  console.log("=========================================================================\n");
}

runSocialGraphExperiment()
  .then(async () => {
    await closeRedisClient();
    process.exit(0);
  })
  .catch(async (err) => {
    console.error("Experiment failed:", err);
    await closeRedisClient();
    process.exit(1);
  });

