import { Kundli } from "../../types/kundli.types";
import { env } from "../../config/env";
import { HttpClient } from "../../utils/http-client";
import { MemoryCache } from "../cache/memory-cache";

export interface KundliAdapter {
  getKundli(userId: string): Promise<Kundli>;
}

export class HttpKundliAdapter implements KundliAdapter {
  private readonly cacheTtlMs = 10 * 60 * 1000;

  constructor(
    private readonly cache: MemoryCache,
    private readonly httpClient: HttpClient,
  ) {}

  async getKundli(userId: string): Promise<Kundli> {
    const cacheKey = `kundli:${userId}`;

    // 1. Check cache first
    const cachedKundli = this.cache.get<Kundli>(cacheKey);

    if (cachedKundli) {
      return cachedKundli;
    }

    // 2. Construct the complete URL separately
    const encodedUserId = encodeURIComponent(userId);

    const url = `${env.upstreamUrl}/kundli/${encodedUserId}`;

    // 3. Call the upstream service
    const kundli = await this.httpClient.get<Kundli>(url, "Kundli Service");

    if (!kundli) {
      throw new Error("Kundli not found");
    }

    // 4. Cache only successful responses
    this.cache.set(cacheKey, kundli, this.cacheTtlMs);

    // 5. Return the result
    return kundli;
  }
}
