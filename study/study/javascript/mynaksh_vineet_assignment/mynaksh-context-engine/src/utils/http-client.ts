import { env } from "../config/env";

export class UpstreamHttpError extends Error {
  constructor(
    message: string,
    public readonly statusCode?: number,
    public readonly serviceName?: string,
  ) {
    super(message);
    this.name = "UpstreamHttpError";
  }
}

interface HttpClientOptions {
  timeoutMs?: number;
  retries?: number;
}

export class HttpClient {
  async get<T>(
    url: string,
    serviceName: string,
    options: HttpClientOptions = {},
  ): Promise<T> {
    const timeoutMs = options.timeoutMs ?? env.upstreamTimeoutMs;

    const retries = options.retries ?? env.upstreamRetries;

    let lastError: unknown;

    for (let attempt = 0; attempt <= retries; attempt++) {
      try {
        return await this.executeGet<T>(url, serviceName, timeoutMs);
      } catch (error) {
        lastError = error;

        if (attempt === retries) {
          break;
        }

        await this.sleep(this.getBackoffDelay(attempt));
      }
    }

    throw lastError instanceof Error
      ? lastError
      : new Error(`Failed to call ${serviceName}`);
  }

  private async executeGet<T>(
    url: string,
    serviceName: string,
    timeoutMs: number,
  ): Promise<T> {
    const controller = new AbortController();

    const timeout = setTimeout(() => {
      controller.abort();
    }, timeoutMs);

    try {
      const response = await fetch(url, {
        method: "GET",
        signal: controller.signal,
        headers: {
          Accept: "application/json",
        },
      });

      if (!response.ok) {
        throw new UpstreamHttpError(
          `${serviceName} returned HTTP ${response.status}`,
          response.status,
          serviceName,
        );
      }

      return (await response.json()) as T;
    } catch (error) {
      if (error instanceof Error && error.name === "AbortError") {
        throw new UpstreamHttpError(
          `${serviceName} timed out after ${timeoutMs}ms`,
          undefined,
          serviceName,
        );
      }

      throw error;
    } finally {
      clearTimeout(timeout);
    }
  }

  private getBackoffDelay(attempt: number): number {
    return Math.min(100 * 2 ** attempt, 1000);
  }

  private sleep(ms: number): Promise<void> {
    return new Promise((resolve) => {
      setTimeout(resolve, ms);
    });
  }
}
