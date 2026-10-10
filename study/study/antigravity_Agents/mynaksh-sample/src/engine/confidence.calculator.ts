import { ContextKey, PersonalizationRule } from "../types/personalization";

export interface ConfidenceEvaluation {
  confidence: "HIGH" | "MEDIUM" | "LOW";
  isDegraded: boolean;
  degradationReason?: string;
}

export class ConfidenceCalculator {
  evaluate(
    rule: PersonalizationRule,
    availableKeys: Set<ContextKey>,
  ): ConfidenceEvaluation {
    const missingMandatoryPrimary = rule.primarySources.filter(
      (source) => source.isMandatory && !availableKeys.has(source.key),
    );

    const missingSecondary = rule.secondarySources.filter(
      (source) => !availableKeys.has(source.key),
    );

    // If critical mandatory sources are missing (e.g. Kundli or Horoscope down)
    if (missingMandatoryPrimary.length > 0) {
      const missingLabels = missingMandatoryPrimary
        .map((s) => s.label)
        .join(", ");
      return {
        confidence: "LOW",
        isDegraded: true,
        degradationReason: `Primary astrological indicators unavailable (${missingLabels}) due to upstream service disruption.`,
      };
    }

    // If secondary context is missing
    if (missingSecondary.length > 0) {
      const missingLabels = missingSecondary.map((s) => s.label).join(", ");
      return {
        confidence: "MEDIUM",
        isDegraded: true,
        degradationReason: `Secondary supporting context unavailable (${missingLabels}).`,
      };
    }

    return {
      confidence: "HIGH",
      isDegraded: false,
    };
  }
}
