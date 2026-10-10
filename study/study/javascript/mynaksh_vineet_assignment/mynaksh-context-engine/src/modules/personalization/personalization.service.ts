import { z } from "zod";

import { UpstreamAggregator } from "../upstream/upstream.aggregator";
import { LLMProvider } from "../llm/llm.provider";

import { PersonalizationEngine } from "./personalization.engine";
import { PromptBuilder } from "./prompt.builder";

import {
  PersonalizationDecision,
  PersonalizeRequest,
  PersonalizeResponse,
} from "../../types/personalization.types";

import { logger } from "../../utils/logger";

export const personalizeRequestSchema = z.object({
  userId: z.string().min(1),
  question: z.string().min(1),
});

export class PersonalizationService {
  constructor(
    private readonly upstreamAggregator: UpstreamAggregator,
    private readonly personalizationEngine: PersonalizationEngine,
    private readonly promptBuilder: PromptBuilder,
    private readonly llmProvider: LLMProvider,
  ) {}

  async personalize(request: PersonalizeRequest): Promise<PersonalizeResponse> {
    // question, userid
    const start = Date.now();

    const context = await this.upstreamAggregator.fetchContext(request.userId); // before intent calssification we are fetching all the upstream

    console.log("Fetched context for user:", request.userId, context);

    if (!context.user) {
      throw new Error("User profile unavailable");
    }

    const decision = this.personalizationEngine.buildDecision(
      // classifying the intent , getting rule configuration based on intent, and for conguration keys to be fetched from aggregated upstream we are using context-resolver
      request.question,
      context,
    );

    const prompt = this.promptBuilder.build(
      request.question,
      decision,
      context,
    );

    logger.info(
      {
        userId: request.userId,
        intent: decision.intent,
        selectedContext: decision.selectedContext,
        promptSize: prompt.length,
      },
      "Personalization decision created",
    );
    console.log(
      "Generated prompt for user:",
      request.userId,
      JSON.stringify(prompt),
    );

    const llmResponse = await this.llmProvider.generate(prompt, {
      maxWords: decision.maxWords,
      temperature: 0.7,
    });

    logger.info(
      {
        userId: request.userId,
        totalLatencyMs: Date.now() - start,
      },
      "Personalization request completed",
    );

    return {
      answer: llmResponse.answer,
      confidence:
        decision.confidence === "LOW" ? "LOW" : llmResponse.confidence,
      sourcesUsed: decision.selectedContext.map((context) => context.label),
    };
  }

  async debug(request: PersonalizeRequest): Promise<PersonalizationDecision> {
    const context = await this.upstreamAggregator.fetchContext(request.userId);

    if (!context.user) {
      throw new Error("User profile unavailable");
    }

    return this.personalizationEngine.buildDecision(request.question, context);
  }
}
