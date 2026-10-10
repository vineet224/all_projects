import { env } from "../../config/env";
import { UserProfile } from "../../types/user.types";
import { MemoryCache } from "../cache/memory-cache";
import { HttpClient } from "../../utils/http-client";

export interface UserAdapter {
  getUser(userId: string): Promise<UserProfile>;
}

export class HttpUserAdapter implements UserAdapter {
  private readonly cacheTtlMs = 10 * 60 * 1000;
  constructor(
    private readonly cache: MemoryCache,
    private readonly httpClient: HttpClient,
  ) {}

  async getUser(userId: string): Promise<UserProfile> {
    const key = `user:${userId}`;

    const cached = this.cache.get<UserProfile>(key);

    if (cached) {
      return cached;
    }

    const encodedUserId = encodeURIComponent(userId);

    const url = `${env.upstreamUrl}/users/${encodedUserId}`;
    const user = await this.httpClient.get<UserProfile>(url, "User Service");

    if (!user) {
      throw new Error("User not found");
    }

    this.cache.set(key, user, this.cacheTtlMs);

    return user;
  }
}
