import { KundliData } from "../../types/domain";
import { MOCK_KUNDLI } from "./mock-data";
import { IUpstreamService } from "./upstream.interface";

export class KundliService implements IUpstreamService<KundliData, string> {
  name = "KundliService";
  private shouldFail = false;
  private delayMs = 30;

  // Test helpers to simulate network failures / slow responses
  setSimulateFailure(fail: boolean) {
    this.shouldFail = fail;
  }

  setDelayMs(ms: number) {
    this.delayMs = ms;
  }

  async fetch(userId: string = "user_101"): Promise<KundliData> {
    if (this.delayMs > 0) {
      await new Promise((r) => setTimeout(r, this.delayMs));
    }

    if (this.shouldFail) {
      throw new Error(
        "KundliService: Upstream database connection timeout (simulated)",
      );
    }

    const kundli = MOCK_KUNDLI[userId];
    if (!kundli) {
      // Return default sample kundli for demo
      return MOCK_KUNDLI["user_101"];
    }

    return kundli;
  }
}
