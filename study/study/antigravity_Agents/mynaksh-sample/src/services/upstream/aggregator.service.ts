import {
  RawUpstreamContext,
  HoroscopeData,
  KundliData,
  PanchangData,
  UserProfile,
} from "../../types/domain";
import { HoroscopeService } from "./horoscope.service";
import { KundliService } from "./kundli.service";
import { PanchangService } from "./panchang.service";
import { UserService } from "./user.service";

export class UpstreamAggregatorService {
  constructor(
    private userService: UserService,
    private kundliService: KundliService,
    private horoscopeService: HoroscopeService,
    private panchangService: PanchangService,
    private timeoutMs: number = 800,
  ) {}

  private async fetchWithTimeout<T>(
    name: string,
    promise: Promise<T>,
  ): Promise<T> {
    let timer: NodeJS.Timeout;
    const timeoutPromise = new Promise<never>((_, reject) => {
      timer = setTimeout(() => {
        reject(
          new Error(`[Timeout] ${name} exceeded ${this.timeoutMs}ms limit`),
        );
      }, this.timeoutMs);
    });

    try {
      const result = await Promise.race([promise, timeoutPromise]);
      clearTimeout(timer!);
      return result;
    } catch (err) {
      clearTimeout(timer!);
      throw err;
    }
  }

  async aggregateContext(userId: string): Promise<RawUpstreamContext> {
    const startTime = Date.now();

    // Launch all 4 upstream services concurrently
    const [userResult, kundliResult, horoscopeResult, panchangResult] =
      await Promise.allSettled([
        this.fetchWithTimeout("UserService", this.userService.fetch(userId)),
        this.fetchWithTimeout(
          "KundliService",
          this.kundliService.fetch(userId),
        ),
        this.fetchWithTimeout(
          "HoroscopeService",
          this.horoscopeService.fetch(userId),
        ),
        this.fetchWithTimeout("PanchangService", this.panchangService.fetch()),
      ]);

    const errors: { serviceName: string; error: string }[] = [];

    // User is the primary baseline
    let user: UserProfile;
    if (userResult.status === "fulfilled") {
      user = userResult.value;
    } else {
      errors.push({
        serviceName: "UserService",
        error: userResult.reason?.message || "Unknown error",
      });
      // Fallback user if user service fails completely
      user = {
        id: userId,
        name: "Seeker",
        language: "en",
        subscription: "free",
        tonePreference: "calm",
        birthDetails: { date: "1995-01-01", time: "12:00", place: "India" },
      };
    }

    let kundli: KundliData | null = null;
    if (kundliResult.status === "fulfilled") {
      kundli = kundliResult.value;
    } else {
      errors.push({
        serviceName: "KundliService",
        error: kundliResult.reason?.message || "Unknown error",
      });
    }

    let horoscope: HoroscopeData | null = null;
    if (horoscopeResult.status === "fulfilled") {
      horoscope = horoscopeResult.value;
    } else {
      errors.push({
        serviceName: "HoroscopeService",
        error: horoscopeResult.reason?.message || "Unknown error",
      });
    }

    let panchang: PanchangData | null = null;
    if (panchangResult.status === "fulfilled") {
      panchang = panchangResult.value;
    } else {
      errors.push({
        serviceName: "PanchangService",
        error: panchangResult.reason?.message || "Unknown error",
      });
    }

    const elapsed = Date.now() - startTime;
    if (errors.length > 0) {
      console.warn(
        `[UpstreamAggregator] Aggregated context with ${errors.length} service failure(s) in ${elapsed}ms:`,
        errors.map((e) => `${e.serviceName}: ${e.error}`).join("; "),
      );
    }

    return {
      user,
      kundli,
      horoscope,
      panchang,
      errors,
    };
  }
}
