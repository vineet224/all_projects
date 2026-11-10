import { AppRedisClient } from "../../client/redis-client.js";

export interface FeedPost {
  postId: string;
  authorId: string;
  content: string;
  timestamp: number;
}

export interface TaskJob {
  jobId: string;
  payload: Record<string, unknown>;
  enqueuedAt: number;
}

export class ListsService {
  constructor(private client: AppRedisClient) {}

  // --------------------------------------------------------------------------
  // 1. Core Push & Pop Operations
  // --------------------------------------------------------------------------

  async lPush(key: string, elements: string | string[]): Promise<number> {
    const list = Array.isArray(elements) ? elements : [elements];
    return await this.client.lPush(key, list);
  }

  async rPush(key: string, elements: string | string[]): Promise<number> {
    const list = Array.isArray(elements) ? elements : [elements];
    return await this.client.rPush(key, list);
  }

  async lPop(key: string, count?: number): Promise<string | string[] | null> {
    if (count !== undefined) {
      return (await this.client.lPopCount(key, count)) as string[];
    }
    const res = await this.client.lPop(key);
    return res ?? null;
  }

  async rPop(key: string, count?: number): Promise<string | string[] | null> {
    if (count !== undefined) {
      return (await this.client.rPopCount(key, count)) as string[];
    }
    const res = await this.client.rPop(key);
    return res ?? null;
  }

  // --------------------------------------------------------------------------
  // 2. Inspection, Trimming & Modification
  // --------------------------------------------------------------------------

  async lRange(key: string, start: number, stop: number): Promise<string[]> {
    return await this.client.lRange(key, start, stop);
  }

  async lIndex(key: string, index: number): Promise<string | null> {
    const res = await this.client.lIndex(key, index);
    return res ?? null;
  }

  async lSet(key: string, index: number, value: string): Promise<string> {
    return await this.client.lSet(key, index, value);
  }

  async lLen(key: string): Promise<number> {
    return await this.client.lLen(key);
  }

  async lTrim(key: string, start: number, stop: number): Promise<string> {
    return await this.client.lTrim(key, start, stop);
  }

  async lRem(key: string, count: number, element: string): Promise<number> {
    return await this.client.lRem(key, count, element);
  }

  // --------------------------------------------------------------------------
  // 3. Reliable Transfers (LMOVE)
  // --------------------------------------------------------------------------

  /**
   * Atomically transfers element between lists (e.g. from pending queue to in-processing queue).
   */
  async lMove(
    source: string,
    destination: string,
    from: "LEFT" | "RIGHT",
    to: "LEFT" | "RIGHT"
  ): Promise<string | null> {
    const res = await this.client.lMove(source, destination, from, to);
    return res ?? null;
  }

  async getObjectEncoding(key: string): Promise<string | null> {
    try {
      const res = await this.client.sendCommand(["OBJECT", "ENCODING", key]);
      return res as string;
    } catch {
      return null;
    }
  }

  // --------------------------------------------------------------------------
  // 4. SDE-2 System Design Patterns
  // --------------------------------------------------------------------------

  /**
   * Pattern A: Chronological Twitter/Timeline Feed
   * Pushes latest post to head of user's timeline and caps list size to maxEntries via LTRIM.
   */
  async pushTimelinePost(userId: string, post: FeedPost, maxEntries = 800): Promise<void> {
    const timelineKey = `timeline:${userId}`;
    const payload = JSON.stringify(post);

    const multi = this.client.multi();
    multi.lPush(timelineKey, payload);
    multi.lTrim(timelineKey, 0, maxEntries - 1);
    await multi.exec();
  }

  async getTimelineFeed(userId: string, page = 0, pageSize = 10): Promise<FeedPost[]> {
    const timelineKey = `timeline:${userId}`;
    const start = page * pageSize;
    const stop = start + pageSize - 1;

    const rawPosts = await this.client.lRange(timelineKey, start, stop);
    return rawPosts.map((raw) => JSON.parse(raw) as FeedPost);
  }

  /**
   * Pattern B: Reliable Task Processing Queue
   * Producer enqueues via LPUSH, consumer atomically moves to in-progress via LMOVE.
   */
  async enqueueTask(queueKey: string, task: TaskJob): Promise<number> {
    return await this.client.lPush(queueKey, JSON.stringify(task));
  }

  async claimTask(queueKey: string, processingKey: string): Promise<TaskJob | null> {
    // Atomically pop from RIGHT (oldest task, FIFO) and push to LEFT of processing queue
    const raw = await this.client.lMove(queueKey, processingKey, "RIGHT", "LEFT");
    if (!raw) return null;
    return JSON.parse(raw) as TaskJob;
  }

  async completeTask(processingKey: string, task: TaskJob): Promise<number> {
    // Remove task from processing queue once worker completes successfully
    return await this.client.lRem(processingKey, 1, JSON.stringify(task));
  }
}

