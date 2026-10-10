import { AggregatedContext } from "../../types/personalization.types";
import { UpstreamDependencies } from "./upstream.types";

interface UpstreamResult<T> {
  value: T | undefined;
  failed: boolean;
}

export class UpstreamAggregator {
  constructor(private readonly dependencies: UpstreamDependencies) {}

  async fetchContext(userId: string): Promise<AggregatedContext> {
    const results = await Promise.allSettled([
      this.dependencies.userAdapter.getUser(userId),
      this.dependencies.kundliAdapter.getKundli(userId),
      this.dependencies.horoscopeAdapter.getHoroscope(userId),
      this.dependencies.panchangAdapter.getPanchang(),
    ]);

    const [userResult, kundliResult, horoscopeResult, panchangResult] = results;

    const user = this.extractValue(userResult);
    const kundli = this.extractValue(kundliResult);
    const horoscope = this.extractValue(horoscopeResult);
    const panchang = this.extractValue(panchangResult);

    const failures = [
      this.getFailureName(userResult, "User Service"),
      this.getFailureName(kundliResult, "Kundli Service"),
      this.getFailureName(horoscopeResult, "Horoscope Service"),
      this.getFailureName(panchangResult, "Panchang Service"),
    ].filter((name): name is string => name !== undefined);

    return {
      user,
      kundli,
      horoscope,
      panchang,
      failures,
    };
  }

  private extractValue<T>(result: PromiseSettledResult<T>): T | undefined {
    return result.status === "fulfilled" ? result.value : undefined;
  }

  private getFailureName<T>(
    result: PromiseSettledResult<T>,
    serviceName: string,
  ): string | undefined {
    return result.status === "rejected" ? serviceName : undefined;
  }
}
