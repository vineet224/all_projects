import { Request, Response, NextFunction } from "express";
import { PersonalizeRequestSchema, PersonalizeResponse } from "../types/api";
import { UpstreamAggregatorService } from "../services/upstream/aggregator.service";
import { PersonalizationEngine } from "../engine/personalization.engine";
import { PromptBuilder } from "../engine/prompt.builder";
import { ILlmProvider } from "../services/llm/llm.interface";
import { PERSONALIZATION_MATRIX } from "../config/personalization.matrix";

export class PersonalizeController {
  constructor(
    private aggregator: UpstreamAggregatorService,
    private engine: PersonalizationEngine,
    private promptBuilder: PromptBuilder,
    private llmProviders: Record<string, ILlmProvider>,
    private defaultProviderKey: string = "mock",
  ) {}

  handle = async (
    req: Request,
    res: Response,
    next: NextFunction,
  ): Promise<void> => {
    const startTime = Date.now();

    try {
      // 1. Validate Input
      const parsed = PersonalizeRequestSchema.parse(req.body);
      const { userId, question, providerOverride } = parsed;

      // 2. Fetch Upstream Data Concurrently with Timeouts
      const rawContext = await this.aggregator.aggregateContext(userId);

      // 3. Process Context via Personalization Engine
      const decision = this.engine.process(question, rawContext);
      const rule = PERSONALIZATION_MATRIX[decision.intent];

      // 4. Construct Structured Prompt
      const builtPrompt = this.promptBuilder.build(
        rule,
        decision,
        rawContext.user,
        question,
      );

      // 5. Calculate & Log Prompt Size
      const promptChars =
        builtPrompt.systemPrompt.length + builtPrompt.userPrompt.length;
      const estimatedPromptTokens = Math.ceil(promptChars / 4);

      console.log(
        `[PromptStats] Intent: "${decision.intent}" | Sources Used: ${decision.selectedContext.length} | Prompt Chars: ${promptChars} (~${estimatedPromptTokens} tokens) | Confidence: ${decision.confidence}`,
      );

      // 6. Invoke LLM Provider
      const providerKey = providerOverride || this.defaultProviderKey;
      const llm = this.llmProviders[providerKey] || this.llmProviders["mock"];

      const llmResult = await llm.generate({
        systemPrompt: builtPrompt.systemPrompt,
        userPrompt: builtPrompt.userPrompt,
        maxOutputTokens: 500,
      });

      const totalLatency = Date.now() - startTime;

      // 7. Formulate Grounded API Response
      const responseBody: PersonalizeResponse = {
        answer: llmResult.content,
        confidence: decision.confidence,
        sourcesUsed: decision.selectedContext,
        metadata: {
          intent: decision.intent,
          latencyMs: totalLatency,
          provider: llm.name,
          isDegraded: decision.isDegraded,
        },
      };

      res.status(200).json(responseBody);
    } catch (err) {
      next(err);
    }
  };
}
