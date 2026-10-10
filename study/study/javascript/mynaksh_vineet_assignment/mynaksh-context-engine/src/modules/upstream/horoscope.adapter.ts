import { Horoscope } from "../../types/horoscope.types";
import { MemoryCache } from "../cache/memory-cache";
import { env } from "../../config/env";
import { HttpClient } from "../../utils/http-client";

export interface HoroscopeAdapter {
  getHoroscope(userId: string): Promise<Horoscope>;
}

export class HttpHoroscopeAdapter implements HoroscopeAdapter {
  private readonly cacheTtlMs = 10 * 60 * 1000;

  constructor(
    private readonly cache: MemoryCache,
    private readonly httpClient: HttpClient,
  ) {}

  async getHoroscope(userId: string): Promise<Horoscope> {
    const cacheKey = `horoscope:${userId}`;

    // 1. Check cache first
    const cached = this.cache.get<Horoscope>(cacheKey);

    if (cached) {
      return cached;
    }

    // 2. Construct the complete URL separately
    const encodedUserId = encodeURIComponent(userId);

    const url = `${env.upstreamUrl}/horoscope/${encodedUserId}`;

    // 3. Call the upstream service
    const horoscope = await this.httpClient.get<Horoscope>(
      url,
      "Horoscope Service",
    );

    if (!horoscope) {
      throw new Error("Horoscope not found");
    }

    // 4. Cache only successful responses
    this.cache.set(cacheKey, horoscope, this.cacheTtlMs);

    // 5. Return the result
    return horoscope;
  }
}
