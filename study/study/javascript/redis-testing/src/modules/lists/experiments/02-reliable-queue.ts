/**
 * Experiment: Reliable Task Queue with Redis Lists (LMOVE Pattern)
 * 
 * SDE-2 System Design Deep Dive:
 * The Problem with Naive RPOP:
 * - If a worker pulls a task via RPOP and crashes (OOM, unhandled exception, network partition),
 *   the job is permanently lost from Redis!
 * 
 * The Reliable Queue Solution (LMOVE):
 * - Atomically pops from the pending queue tail AND pushes to an in-processing queue head.
 * - If worker crashes, a supervisor process can inspect the processing queue and re-enqueue or dead-letter.
 * - When worker completes successfully, it deletes the item from processing queue via LREM.
 */
import { getRedisClient, closeRedisClient } from "../../../client/redis-client.js";
import { createTestNamespace } from "../../../client/key-manager.js";

async function runReliableQueueExperiment() {
  const client = await getRedisClient();
  const ns = createTestNamespace("exp-queue");

  console.log("\n=========================================================================");
  console.log("📨 Lists Experiment: Reliable Task Queue with LMOVE (SDE-2 System Design)");
  console.log("=========================================================================\n");

  const pendingQ = ns.key("queue:pending");
  const processingQ = ns.key("queue:processing");
  const deadLetterQ = ns.key("queue:dlq");

  // 1. Producer enqueues 3 tasks
  console.log("1️⃣  Producer enqueuing 3 background jobs via LPUSH...");
  const jobs = [
    { id: "job_001", type: "SEND_WELCOME_EMAIL", email: "user1@domain.com" },
    { id: "job_002", type: "GENERATE_PDF_INVOICE", orderId: "ord_9981" },
    { id: "job_003", type: "PROCESS_PAYMENT", amount: 150.0 },
  ];

  for (const job of jobs) {
    await client.lPush(pendingQ, JSON.stringify(job));
  }
  console.log(`   Pending Queue Length: ${await client.lLen(pendingQ)}\n`);

  // 2. Consumer Worker 1 atomically claims task
  console.log("2️⃣  Worker 1 claiming next available job via atomic LMOVE (RIGHT -> LEFT)...");
  const claimedRaw = await client.lMove(pendingQ, processingQ, "RIGHT", "LEFT");
  const claimedJob = JSON.parse(claimedRaw!);
  console.log(`   Claimed Job: [${claimedJob.id}] (${claimedJob.type})`);
  console.log(`   Pending Queue remaining:    ${await client.lLen(pendingQ)}`);
  console.log(`   In-Processing Queue count:  ${await client.lLen(processingQ)}\n`);

  // 3. Worker Crash Simulation
  console.log("3️⃣  Simulating Worker 1 Crash (Uncaught exception / Pod restart)...");
  console.log(`   Worker crashed before acknowledging!`);
  console.log(`   Is the job lost? Let's check Processing Queue:`);
  const inFlight = await client.lRange(processingQ, 0, -1);
  console.log(`   In-Flight items in Redis:`, inFlight.map((s) => JSON.parse(s).id));
  console.log(`   ✅ Zero Message Loss! Job remains safely stored in "${processingQ}".\n`);

  // 4. Recovery & Dead-Letter Routing
  console.log("4️⃣  Supervisor Recovery Process runs:");
  console.log(`   Re-routing stuck job "${claimedJob.id}" to Dead-Letter Queue (DLQ) via LMOVE...`);
  await client.lMove(processingQ, deadLetterQ, "RIGHT", "LEFT");

  console.log(`   In-Processing Queue count: ${await client.lLen(processingQ)}`);
  console.log(`   Dead-Letter Queue count:   ${await client.lLen(deadLetterQ)}`);

  console.log("\n💡 SDE-2 Message Delivery Semantics Insight:");
  console.log("   - Plain RPOP provides 'At-Most-Once' delivery (messages can be lost on crash).");
  console.log("   - LMOVE provides 'At-Least-Once' delivery (messages are guaranteed retained until explicitly acknowledged).");
  console.log("   - Downstream consumers MUST be idempotent to handle crash recovery without duplicate business side-effects!");

  await ns.cleanup(client);
  console.log("\n🧹 Cleaned up experiment keys.");
  console.log("=========================================================================\n");
}

runReliableQueueExperiment()
  .then(async () => {
    await closeRedisClient();
    process.exit(0);
  })
  .catch(async (err) => {
    console.error("Experiment failed:", err);
    await closeRedisClient();
    process.exit(1);
  });

