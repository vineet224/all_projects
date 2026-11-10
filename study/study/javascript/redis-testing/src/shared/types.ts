export interface BenchmarkMetric {
  name: string;
  operations: number;
  totalTimeMs: number;
  opsPerSecond: number;
  avgLatencyMs: number;
  p50LatencyMs: number;
  p90LatencyMs: number;
  p95LatencyMs: number;
  p99LatencyMs: number;
  minLatencyMs: number;
  maxLatencyMs: number;
}

export interface DistributedLockOptions {
  ttlMs?: number;
  retryCount?: number;
  retryDelayMs?: number;
}

export interface LockAcquisitionResult {
  acquired: boolean;
  lockValue?: string;
  expiresAt?: number;
}

