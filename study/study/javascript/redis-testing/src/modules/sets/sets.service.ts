import { AppRedisClient } from "../../client/redis-client.js";

export class SetsService {
  constructor(private client: AppRedisClient) {}

  // --------------------------------------------------------------------------
  // 1. Core Set Operations
  // --------------------------------------------------------------------------

  async sAdd(key: string, members: string | string[]): Promise<number> {
    const list = Array.isArray(members) ? members : [members];
    return await this.client.sAdd(key, list);
  }

  async sRem(key: string, members: string | string[]): Promise<number> {
    const list = Array.isArray(members) ? members : [members];
    return await this.client.sRem(key, list);
  }

  async sCard(key: string): Promise<number> {
    return await this.client.sCard(key);
  }

  async sIsMember(key: string, member: string): Promise<boolean> {
    return await this.client.sIsMember(key, member);
  }

  async sMIsMember(key: string, members: string[]): Promise<boolean[]> {
    if (members.length === 0) return [];
    return await this.client.smIsMember(key, members);
  }

  async sMembers(key: string): Promise<string[]> {
    return await this.client.sMembers(key);
  }

  // --------------------------------------------------------------------------
  // 2. Random Sampling & Pop
  // --------------------------------------------------------------------------

  async sPop(key: string, count?: number): Promise<string | string[] | null> {
    if (count !== undefined) {
      return (await this.client.sPop(key, count)) as string[];
    }
    const res = await this.client.sPop(key);
    return res ?? null;
  }

  async sRandMember(key: string): Promise<string | null> {
    const res = await this.client.sRandMember(key);
    return res ?? null;
  }

  async sRandMemberCount(key: string, count: number): Promise<string[]> {
    const res = await this.client.sRandMemberCount(key, count);
    return res as string[];
  }

  // --------------------------------------------------------------------------
  // 3. Set Algebra (Intersection, Union, Difference)
  // --------------------------------------------------------------------------

  async sInter(keys: string[]): Promise<string[]> {
    return await this.client.sInter(keys);
  }

  async sUnion(keys: string[]): Promise<string[]> {
    return await this.client.sUnion(keys);
  }

  async sDiff(keys: string[]): Promise<string[]> {
    return await this.client.sDiff(keys);
  }

  async sInterStore(destination: string, keys: string[]): Promise<number> {
    const res = await this.client.sInterStore(destination, keys);
    return typeof res === "number" ? res : (res as any).length;
  }

  async sUnionStore(destination: string, keys: string[]): Promise<number> {
    return await this.client.sUnionStore(destination, keys);
  }

  async sDiffStore(destination: string, keys: string[]): Promise<number> {
    return await this.client.sDiffStore(destination, keys);
  }

  /**
   * Redis 7.0+ SINTERCARD: returns cardinality of intersection without transferring members.
   */
  async sInterCard(keys: string[], limit?: number): Promise<number> {
    if (limit) {
      return await this.client.sInterCard(keys, limit);
    }
    return await this.client.sInterCard(keys);
  }

  async sScan(
    key: string,
    cursor: number,
    options?: { MATCH?: string; COUNT?: number }
  ): Promise<{ cursor: number; members: string[] }> {
    const res = await this.client.sScan(key, cursor, options);
    return {
      cursor: res.cursor,
      members: res.members,
    };
  }

  // --------------------------------------------------------------------------
  // 4. Memory & Encodings
  // --------------------------------------------------------------------------

  async getObjectEncoding(key: string): Promise<string | null> {
    try {
      const res = await this.client.sendCommand(["OBJECT", "ENCODING", key]);
      return res as string;
    } catch {
      return null;
    }
  }

  // --------------------------------------------------------------------------
  // 5. SDE-2 System Design Patterns
  // --------------------------------------------------------------------------

  /**
   * Social Graph: Follow a user (bi-directional following & followers sets).
   */
  async followUser(followerId: string, targetUserId: string): Promise<void> {
    const followingKey = `user:${followerId}:following`;
    const followersKey = `user:${targetUserId}:followers`;

    // Multi/exec transaction for atomic mutual graph update
    const multi = this.client.multi();
    multi.sAdd(followingKey, targetUserId);
    multi.sAdd(followersKey, followerId);
    await multi.exec();
  }

  async unfollowUser(followerId: string, targetUserId: string): Promise<void> {
    const followingKey = `user:${followerId}:following`;
    const followersKey = `user:${targetUserId}:followers`;

    const multi = this.client.multi();
    multi.sRem(followingKey, targetUserId);
    multi.sRem(followersKey, followerId);
    await multi.exec();
  }

  /**
   * Find mutual connections between two users (SINTER).
   */
  async getMutualFollowing(userAId: string, userBId: string): Promise<string[]> {
    return await this.client.sInter([`user:${userAId}:following`, `user:${userBId}:following`]);
  }

  /**
   * Friend recommendations: Users that User B follows which User A does not yet follow (SDIFF).
   */
  async getRecommendedFollows(userAId: string, userBId: string): Promise<string[]> {
    return await this.client.sDiff([`user:${userBId}:following`, `user:${userAId}:following`]);
  }
}
