import { HoroscopeData } from "../../types/domain";
import { MOCK_HOROSCOPE } from "./mock-data";
import { IUpstreamService } from "./upstream.interface";

export class HoroscopeService implements IUpstreamService<
  HoroscopeData,
  string
> {
  name = "HoroscopeService";
  private shouldFail = false;
  private delayMs = 30;

  setSimulateFailure(fail: boolean) {
    this.shouldFail = fail;
  }

  setDelayMs(ms: number) {
    this.delayMs = ms;
  }

  async fetch(userId: string = "user_101"): Promise<HoroscopeData> {
    if (this.delayMs > 0) {
      await new Promise((r) => setTimeout(r, this.delayMs));
    }

    if (this.shouldFail) {
      throw new Error(
        "HoroscopeService: Upstream 503 Service Unavailable (simulated)",
      );
    }

    return (
      MOCK_HOROSCOPE[userId] || {
        career: "Opportunities for steady growth appear on your path.",
        finance: "Maintain financial prudence.",
        health: "Focus on regular mindfulness and rest.",
        relationship: "Warm communication brings clarity.",
      }
    );
  }
}
