/**
 * Experiment 01: Redis SDS (Simple Dynamic String) Encodings & Memory Internals
 * 
 * Demonstrates:
 * 1. INT encoding: numbers stored directly as 64-bit integers.
 * 2. EMBSTR encoding: short strings allocated in a single jemalloc memory chunk (redisObject + sdshdr).
 * 3. RAW encoding: strings requiring dual memory allocations.
 * 4. Encoding mutation: why modifying an EMBSTR immediately forces re-allocation to RAW.
 * 5. MEMORY USAGE comparisons.
 */
import { getRedisClient, closeRedisClient } from "../../../client/redis-client.js";
import { createTestNamespace } from "../../../client/key-manager.js";

async function runEncodingsExperiment() {
  const client = await getRedisClient();
  const ns = createTestNamespace("exp-encodings");

  console.log("\n=========================================================================");
  console.log("🔬 Experiment 01: Redis String Encodings & Memory Internals (SDE-2 Deep Dive)");
  console.log("=========================================================================\n");

  const kInt = ns.key("i");
  const kEmb = ns.key("e");
  const kRaw = ns.key("r");

  // 1. INT Encoding
  await client.set(kInt, 987654321);
  const encInt = await client.sendCommand(["OBJECT", "ENCODING", kInt]);
  const memInt = await client.sendCommand(["MEMORY", "USAGE", kInt]);
  console.log(`[INT] Key: "${kInt}" | Value: 987654321`);
  console.log(`      Encoding: ${encInt}`);
  console.log(`      Memory Usage: ${memInt} bytes\n`);

  // 2. EMBSTR Encoding
  // Short string where redisObject + SDS fits in <= 64-byte jemalloc chunk
  const shortStr = "Cache";
  await client.set(kEmb, shortStr);
  const encEmb = await client.sendCommand(["OBJECT", "ENCODING", kEmb]);
  const memEmb = await client.sendCommand(["MEMORY", "USAGE", kEmb]);
  console.log(`[EMBSTR] Key: "${kEmb}" | Value: "${shortStr}" (Length: ${shortStr.length})`);
  console.log(`         Encoding: ${encEmb}`);
  console.log(`         Memory Usage: ${memEmb} bytes`);
  console.log(`         Note: redisObject (16B) + sdshdr8 (3B) + data (5B) + null byte (1B) allocated in 1 continuous jemalloc chunk.\n`);

  // 3. RAW Encoding
  // String > 44 bytes
  const longStr = "A".repeat(50);
  await client.set(kRaw, longStr);
  const encRaw = await client.sendCommand(["OBJECT", "ENCODING", kRaw]);
  const memRaw = await client.sendCommand(["MEMORY", "USAGE", kRaw]);
  console.log(`[RAW] Key: "${kRaw}" | Value length: ${longStr.length} bytes`);
  console.log(`      Encoding: ${encRaw}`);
  console.log(`      Memory Usage: ${memRaw} bytes`);
  console.log(`      Note: Requires two separate memory allocations (one for redisObject, one for SDS).\n`);

  // 4. EMBSTR Immutability & Conversion
  console.log("🔄 Demonstrating EMBSTR -> RAW conversion on modification...");
  console.log(`   Initial encoding of "${kEmb}": ${encEmb}`);
  await client.append(kEmb, " (updated)");
  const encAfterAppend = await client.sendCommand(["OBJECT", "ENCODING", kEmb]);
  const memAfterAppend = await client.sendCommand(["MEMORY", "USAGE", kEmb]);
  console.log(`   After APPEND encoding: ${encAfterAppend}`);
  console.log(`   After APPEND memory usage: ${memAfterAppend} bytes`);
  console.log(`   Explanation: Redis EMBSTR objects are immutable. Any write or append operation immediately re-allocates the SDS to 'raw' encoding!\n`);

  // Clean up
  await ns.cleanup(client);
  console.log("🧹 Cleaned up experiment keys.");
  console.log("=========================================================================\n");
}

runEncodingsExperiment()
  .then(async () => {
    await closeRedisClient();
    process.exit(0);
  })
  .catch(async (err) => {
    console.error("Experiment failed:", err);
    await closeRedisClient();
    process.exit(1);
  });
