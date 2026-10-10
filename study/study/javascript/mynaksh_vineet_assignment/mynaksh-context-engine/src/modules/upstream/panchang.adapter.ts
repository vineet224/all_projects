import { env } from "../../config/env";
import { Panchang } from "../../types/panchang.types";
import { HttpClient } from "../../utils/http-client";
import { MemoryCache } from "../cache/memory-cache";

export interface PanchangAdapter {
  getPanchang(): Promise<Panchang>;
}

export class HttpPanchangAdapter implements PanchangAdapter {
  private readonly cacheTtlMs = 10 * 60 * 1000;

  constructor(
    private readonly cache: MemoryCache,
    private readonly httpClient: HttpClient,
  ) {}

  async getPanchang(): Promise<Panchang> {
    const cacheKey = `panchang:current`;

    const cached = this.cache.get<Panchang>(cacheKey);

    if (cached) {
      return cached;
    }

    const url = `${env.upstreamUrl}/panchang`;
    const panchang = await this.httpClient.get<Panchang>(
      url,
      "Panchang Service",
    );
    if (!panchang) {
      throw new Error("Panchang not found");
    }

    this.cache.set(cacheKey, panchang, this.cacheTtlMs);

    return panchang;
  }
}
