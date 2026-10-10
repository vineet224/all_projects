import { Request, Response, NextFunction } from "express";
import {
  PersonalizeRequestSchema,
  DebugPersonalizationResponse,
} from "../types/api";
import { UpstreamAggregatorService } from "../services/upstream/aggregator.service";
import { PersonalizationEngine } from "../engine/personalization.engine";
import { PromptBuilder } from "../engine/prompt.builder";
import { PERSONALIZATION_MATRIX } from "../config/personalization.matrix";

export class DebugController {
  constructor(
    private aggregator: UpstreamAggregatorService,
    private engine: PersonalizationEngine,
    private promptBuilder: PromptBuilder,
  ) {}

  handle = async (
    req: Request,
    res: Response,
    next: NextFunction,
  ): Promise<void> => {
    try {
      // 1. Validate Input
      const parsed = PersonalizeRequestSchema.parse(req.body);
      const { userId, question } = parsed;

      // 2. Fetch Upstream Data Concurrently
      const rawContext = await this.aggregator.aggregateContext(userId);

      // 3. Process Context via Personalization Engine (NO LLM invocation)
      const decision = this.engine.process(question, rawContext);
      const rule = PERSONALIZATION_MATRIX[decision.intent];

      // 4. Construct Structured Prompt to show final output
      const builtPrompt = this.promptBuilder.build(
        rule,
        decision,
        rawContext.user,
        question,
      );

      const responseBody: DebugPersonalizationResponse = {
        intent: decision.intent,
        selectedContext: decision.selectedContext,
        excludedContext: decision.excludedContext,
        language: decision.language,
        tone: decision.tone,
        maxWords: decision.maxWords,
        confidence: decision.confidence,
        isDegraded: decision.isDegraded,
        degradationReason: decision.degradationReason,
        assembledPrompt: {
          systemDirective: rule.systemDirective,
          contextText: decision.promptContextText,
        },
      };

      res.status(200).json(responseBody);
    } catch (err) {
      next(err);
    }
  };
}
