import { ICacheService } from "../cache/cache.interface";
import { PanchangData } from "../../types/domain";
import { MOCK_PANCHANG } from "./mock-data";
import { IUpstreamService } from "./upstream.interface";

export class PanchangService implements IUpstreamService<PanchangData, void> {
  name = "PanchangService";
  private shouldFail = false;

  constructor(
    private cache: ICacheService,
    private cacheTtlMs: number = 21600000, // 6 hours
  ) {}

  setSimulateFailure(fail: boolean) {
    this.shouldFail = fail;
  }

  async fetch(): Promise<PanchangData> {
    const todayKey = `panchang:${new Date().toISOString().slice(0, 10)}`;
    const cached = await this.cache.get<PanchangData>(todayKey);
    if (cached) return cached;

    await new Promise((r) => setTimeout(r, 20));

    if (this.shouldFail) {
      throw new Error(
        "PanchangService: Upstream ephemeris calculation error (simulated)",
      );
    }

    await this.cache.set(todayKey, MOCK_PANCHANG, this.cacheTtlMs);
    return MOCK_PANCHANG;
  }
}
