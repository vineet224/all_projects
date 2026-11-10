/**
 * Experiment: Redis Hash Encodings (listpack vs hashtable)
 * 
 * Demonstrates:
 * 1. Compact listpack encoding for small hashes.
 * 2. Threshold-driven transition to hashtable when field length exceeds 64 bytes.
 * 3. Memory overhead comparison.
 */
import { getRedisClient, closeRedisClient } from "../../../client/redis-client.js";
import { createTestNamespace } from "../../../client/key-manager.js";

async function runHashEncodingsExperiment() {
  const client = await getRedisClient();
  const ns = createTestNamespace("exp-hash-enc");

  console.log("\n=========================================================================");
  console.log("🔬 Hashes Experiment: listpack vs hashtable Encodings (SDE-2 Deep Dive)");
  console.log("=========================================================================\n");

  const compactKey = ns.key("compact");
  const largeFieldKey = ns.key("large_field");

  // 1. Compact listpack hash (fields and values <= 64 bytes)
  await client.hSet(compactKey, {
    id: "usr_1001",
    name: "John Doe",
    email: "john@example.com",
    role: "engineer",
  });

  const compactEnc = await client.sendCommand(["OBJECT", "ENCODING", compactKey]);
  const compactMem = await client.sendCommand(["MEMORY", "USAGE", compactKey]);
  console.log(`[Compact Hash] Key: "${compactKey}"`);
  console.log(`               Encoding: ${compactEnc} (contiguous byte array, 0 dictEntry pointer overhead)`);
  console.log(`               Memory Usage: ${compactMem} bytes\n`);

  // 2. Hash exceeding 64-byte value threshold -> converts to hashtable
  const longValue = "x".repeat(65);
  await client.hSet(largeFieldKey, "bio", longValue);

  const largeEnc = await client.sendCommand(["OBJECT", "ENCODING", largeFieldKey]);
  const largeMem = await client.sendCommand(["MEMORY", "USAGE", largeFieldKey]);
  console.log(`[Large Field Hash] Value length: ${longValue.length} bytes (exceeds hash-max-listpack-value: 64)`);
  console.log(`                   Encoding: ${largeEnc}`);
  console.log(`                   Memory Usage: ${largeMem} bytes\n`);

  console.log("💡 SDE-2 Interview Insight:");
  console.log("   - 'listpack' stores fields/values sequentially in a single contiguous memory buffer.");
  console.log("   - Lookups in listpack are O(N), but CPU cache locality makes it faster than hashtable for small N (<= 512).");
  console.log("   - When any field/value exceeds 64 bytes or entry count exceeds 512, Redis converts it to 'hashtable' (O(1) lookups, higher RAM).");

  await ns.cleanup(client);
  console.log("\n🧹 Cleaned up experiment keys.");
  console.log("=========================================================================\n");
}

runHashEncodingsExperiment()
  .then(async () => {
    await closeRedisClient();
    process.exit(0);
  })
  .catch(async (err) => {
    console.error("Experiment failed:", err);
    await closeRedisClient();
    process.exit(1);
  });

