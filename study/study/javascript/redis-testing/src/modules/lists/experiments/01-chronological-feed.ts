/**
 * Experiment: Chronological Timeline Feed with Redis Lists (Twitter/Instagram architecture)
 * 
 * SDE-2 System Design Deep Dive:
 * Demonstrates:
 * 1. Fast O(1) ingestion via LPUSH at list head.
 * 2. Capped timeline memory via atomic LTRIM (e.g. keeping only top 800 recent posts).
 * 3. Paginated feed retrieval via LRANGE.
 * 4. Fan-out on write (push model) vs Fan-out on read (pull model) tradeoffs.
 */
import { getRedisClient, closeRedisClient } from "../../../client/redis-client.js";
import { createTestNamespace } from "../../../client/key-manager.js";

async function runChronologicalFeedExperiment() {
  const client = await getRedisClient();
  const ns = createTestNamespace("exp-feed");

  console.log("\n=========================================================================");
  console.log("📱 Lists Experiment: Chronological Timeline Feed (SDE-2 System Design)");
  console.log("=========================================================================\n");

  const timelineKey = ns.key("timeline:user:alice");
  const maxFeedSize = 5; // Bounded window for demonstration

  console.log(`1️⃣  Simulating 8 incoming posts published by accounts Alice follows...`);
  console.log(`   Policy: Keep only the ${maxFeedSize} most recent posts (LPUSH + LTRIM).\n`);

  for (let i = 1; i <= 8; i++) {
    const post = {
      id: `tweet_${i}`,
      author: `@creator_${i % 3}`,
      text: `Important update #${i} about AI systems!`,
      publishedAt: new Date(Date.now() + i * 1000).toISOString(),
    };

    // Atomic pipeline: LPUSH to head + LTRIM to maxFeedSize
    const multi = client.multi();
    multi.lPush(timelineKey, JSON.stringify(post));
    multi.lTrim(timelineKey, 0, maxFeedSize - 1);
    await multi.exec();
    console.log(`   Pushed: ${post.id} (Current timeline length: ${await client.lLen(timelineKey)})`);
  }

  // Inspect the capped timeline
  console.log(`\n2️⃣  Retrieving Alice's Timeline (LRANGE 0 -1):`);
  const rawPosts = await client.lRange(timelineKey, 0, -1);
  rawPosts.forEach((raw, idx) => {
    const p = JSON.parse(raw);
    console.log(`   Position #${idx + 1}: [${p.id}] by ${p.author} -> "${p.text}"`);
  });

  console.log(`\n3️⃣  Pagination Simulation (Page Size = 2):`);
  // Page 1: 0 to 1
  const page1 = (await client.lRange(timelineKey, 0, 1)).map((r) => JSON.parse(r).id);
  console.log(`   Page 1 (indices 0..1):`, page1);

  // Page 2: 2 to 3
  const page2 = (await client.lRange(timelineKey, 2, 3)).map((r) => JSON.parse(r).id);
  console.log(`   Page 2 (indices 2..3):`, page2);

  console.log("\n💡 SDE-2 Fan-Out Tradeoff Insight:");
  console.log("   - Fan-out on Write (Push model): When user posts, push post ID to each follower's timeline list.");
  console.log("     Pros: Reading a timeline is O(1) LRANGE. Ultra-low read latency.");
  console.log("     Cons: Celebrity problem (millions of followers = millions of LPUSH operations).");
  console.log("   - Hybrid Model: Push to active users' lists; fetch celebrity posts on read (Pull model) and merge.");

  await ns.cleanup(client);
  console.log("\n🧹 Cleaned up experiment keys.");
  console.log("=========================================================================\n");
}

runChronologicalFeedExperiment()
  .then(async () => {
    await closeRedisClient();
    process.exit(0);
  })
  .catch(async (err) => {
    console.error("Experiment failed:", err);
    await closeRedisClient();
    process.exit(1);
  });

