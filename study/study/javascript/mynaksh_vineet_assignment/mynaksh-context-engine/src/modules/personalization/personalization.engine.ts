import { personalizationRules } from "../../config/personalization.rules";

import {
  AggregatedContext,
  PersonalizationDecision,
} from "../../types/personalization.types";

import { IntentDetector } from "./intent.detector";
import { ContextSelector } from "./context.selector";

export class PersonalizationEngine {
  constructor(
    private readonly intentDetector: IntentDetector,
    private readonly contextSelector: ContextSelector,
  ) {}

  buildDecision(
    question: string,
    context: AggregatedContext,
  ): PersonalizationDecision {
    const intentResult = this.intentDetector.detect(question); // classifying intent this is single intent from the question not the array on intent

    const { selectedContext, excludedContext } = this.contextSelector.select(
      intentResult.intent,
      context,
    );

    console.log("Selected Context:", selectedContext);
    console.log("Excluded Context:", excludedContext);

    const user = context.user;

    const language = user?.language === "hi" ? "Hindi" : "English";

    const tone =
      user?.tonePreference === "motivational"
        ? "Motivational"
        : user?.tonePreference === "professional"
          ? "Professional"
          : user?.tonePreference === "friendly"
            ? "Friendly"
            : "Concise";

    const maxWords = user?.subscription === "premium" ? 250 : 150;

    let confidence = intentResult.confidence;

    const rule = personalizationRules[intentResult.intent];

    const hasPrimaryContext = rule.primaryContext.every((key) =>
      selectedContext.some((item) => item.key === key),
    );

    if (!hasPrimaryContext) {
      confidence = "LOW";
    }

    if (context.failures.length > 0) {
      if (confidence === "HIGH") {
        confidence = "MEDIUM";
      } else if (confidence === "MEDIUM") {
        confidence = "LOW";
      }
    }

    return {
      intent: intentResult.intent,
      language,
      tone,
      maxWords,
      selectedContext,
      excludedContext,
      confidence,
    };
  }

  private matchesKey(label: string, key: string): boolean {
    const mapping: Record<string, string> = {
      careerHoroscope: "Career Horoscope",
      relationshipHoroscope: "Relationship Horoscope",
      healthHoroscope: "Health Horoscope",
      financeHoroscope: "Finance Horoscope",
      house10: "10th House",
      house7: "7th House",
      house6: "6th House",
      moonSign: "Moon Sign",
      currentDasha: "Current Dasha",
      panchang: "Today's Panchang",
    };

    return mapping[key] === label;
  }
}
