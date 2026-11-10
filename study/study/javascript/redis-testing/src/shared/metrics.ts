import { BenchmarkMetric } from "./types.js";

/**
 * Calculates latency percentiles and throughput from an array of sample latencies in milliseconds.
 */
export function calculateMetrics(
  name: string,
  latenciesMs: number[],
  totalTimeMs: number
): BenchmarkMetric {
  if (latenciesMs.length === 0) {
    return {
      name,
      operations: 0,
      totalTimeMs,
      opsPerSecond: 0,
      avgLatencyMs: 0,
      p50LatencyMs: 0,
      p90LatencyMs: 0,
      p95LatencyMs: 0,
      p99LatencyMs: 0,
      minLatencyMs: 0,
      maxLatencyMs: 0,
    };
  }

  const sorted = [...latenciesMs].sort((a, b) => a - b);
  const count = sorted.length;
  const sum = sorted.reduce((acc, val) => acc + val, 0);

  const getPercentile = (p: number): number => {
    const idx = Math.min(Math.floor((p / 100) * count), count - 1);
    return sorted[idx];
  };

  return {
    name,
    operations: count,
    totalTimeMs: Number(totalTimeMs.toFixed(2)),
    opsPerSecond: Number(((count / (totalTimeMs / 1000)) || 0).toFixed(2)),
    avgLatencyMs: Number((sum / count).toFixed(3)),
    p50LatencyMs: Number(getPercentile(50).toFixed(3)),
    p90LatencyMs: Number(getPercentile(90).toFixed(3)),
    p95LatencyMs: Number(getPercentile(95).toFixed(3)),
    p99LatencyMs: Number(getPercentile(99).toFixed(3)),
    minLatencyMs: Number(sorted[0].toFixed(3)),
    maxLatencyMs: Number(sorted[count - 1].toFixed(3)),
  };
}

/**
 * Utility to measure a single asynchronous block in milliseconds with sub-millisecond precision.
 */
export async function measureTimeMs(fn: () => Promise<void>): Promise<number> {
  const start = process.hrtime.bigint();
  await fn();
  const end = process.hrtime.bigint();
  return Number(end - start) / 1_000_000;
}

/**
 * Prints a clean markdown/ASCII comparison table for an array of BenchmarkMetric results.
 */
export function printMetricsTable(metrics: BenchmarkMetric[]): void {
  console.log("\n=========================================================================================================");
  console.log(
    `| ${"Operation / Strategy".padEnd(28)} | ${"Ops".padStart(7)} | ${"Time(ms)".padStart(9)} | ${"Ops/sec".padStart(11)} | ${"Avg(ms)".padStart(8)} | ${"p50(ms)".padStart(8)} | ${"p99(ms)".padStart(8)} |`
  );
  console.log("---------------------------------------------------------------------------------------------------------");
  for (const m of metrics) {
    console.log(
      `| ${m.name.padEnd(28)} | ${m.operations.toString().padStart(7)} | ${m.totalTimeMs.toFixed(1).padStart(9)} | ${m.opsPerSecond.toFixed(1).padStart(11)} | ${m.avgLatencyMs.toFixed(3).padStart(8)} | ${m.p50LatencyMs.toFixed(3).padStart(8)} | ${m.p99LatencyMs.toFixed(3).padStart(8)} |`
    );
  }
  console.log("=========================================================================================================\n");
}

