import { ICacheService } from "../cache/cache.interface";
import { UserProfile } from "../../types/domain";
import { MOCK_USERS } from "./mock-data";
import { IUpstreamService } from "./upstream.interface";

export class UserService implements IUpstreamService<UserProfile, string> {
  name = "UserService";

  constructor(
    private cache: ICacheService,
    private cacheTtlMs: number = 3600000,
  ) {}

  async fetch(userId: string = "user_101"): Promise<UserProfile> {
    const cacheKey = `user:${userId}`;
    const cached = await this.cache.get<UserProfile>(cacheKey);
    if (cached) return cached;

    // Simulate minimal network latency
    await new Promise((r) => setTimeout(r, 25));

    const user = MOCK_USERS[userId];
    if (!user) {
      // Fallback default mock user if unknown ID
      const fallbackUser: UserProfile = {
        id: userId,
        name: "Seeker",
        language: "en",
        subscription: "free",
        tonePreference: "calm",
        birthDetails: {
          date: "1995-01-01",
          time: "12:00",
          place: "India",
        },
      };
      await this.cache.set(cacheKey, fallbackUser, this.cacheTtlMs);
      return fallbackUser;
    }

    await this.cache.set(cacheKey, user, this.cacheTtlMs);
    return user;
  }
}
