import { PERSONALIZATION_MATRIX } from "../config/personalization.matrix";
import { RawUpstreamContext } from "../types/domain";
import { PersonalizationDecision } from "../types/personalization";
import { ConfidenceCalculator } from "./confidence.calculator";
import { ContextPruner } from "./context.pruner";
import {
  IIntentClassifier,
  KeywordIntentClassifier,
} from "./intent.classifier";

export class PersonalizationEngine {
  constructor(
    private intentClassifier: IIntentClassifier = new KeywordIntentClassifier(),
    private contextPruner: ContextPruner = new ContextPruner(),
    private confidenceCalculator: ConfidenceCalculator = new ConfidenceCalculator(),
  ) {}

  process(
    question: string,
    rawContext: RawUpstreamContext,
  ): PersonalizationDecision {
    // 1. Determine User Intent
    const intent = this.intentClassifier.classify(question);
    const rule =
      PERSONALIZATION_MATRIX[intent] || PERSONALIZATION_MATRIX.general;

    // 2. Select Relevant Context & Exclude Noise
    const prunedResult = this.contextPruner.prune(rule, rawContext);

    // 3. Deterministically Evaluate Confidence Based on Data Completeness
    const confidenceResult = this.confidenceCalculator.evaluate(
      rule,
      prunedResult.availableKeySet,
    );

    // 4. Resolve Personalization Attributes (User overrides default)
    const language = rawContext.user.language === "hi" ? "Hindi" : "English";
    const tone = rawContext.user.tonePreference || "empowering";

    // Adjust word length based on subscription
    let maxWords = rule.defaultMaxWords;
    if (
      rawContext.user.subscription === "premium" ||
      rawContext.user.subscription === "vip"
    ) {
      maxWords += 50; // Premium users receive slightly deeper analysis
    }

    return {
      intent,
      language,
      tone,
      maxWords,
      selectedContext: prunedResult.selectedContext,
      excludedContext: prunedResult.excludedContext,
      availableItems: prunedResult.extractedItems,
      confidence: confidenceResult.confidence,
      isDegraded: confidenceResult.isDegraded,
      degradationReason: confidenceResult.degradationReason,
      promptContextText: prunedResult.formattedContextText,
    };
  }
}
