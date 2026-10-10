import { Intent, IntentResult } from "../../types/personalization.types";

const keywords: Record<Intent, string[]> = {
  career: [
    "job",
    "career",
    "work",
    "profession",
    "promotion",
    "office",
    "change my job",
    "switch job",
  ],

  relationship: [
    "relationship",
    "partner",
    "marriage",
    "love",
    "romance",
    "boyfriend",
    "girlfriend",
  ],

  health: ["health", "sleep", "fitness", "wellness", "body", "stress"],

  finance: [
    "money",
    "finance",
    "financial",
    "investment",
    "invest",
    "wealth",
    "income",
  ],

  general: [],
};

export class IntentDetector {
  detect(question: string): IntentResult {
    const normalized = question.toLowerCase();

    const scores: Record<Intent, number> = {
      career: 0,
      relationship: 0,
      health: 0,
      finance: 0,
      general: 0,
    };

    for (const intent of Object.keys(keywords) as Intent[]) {
      for (const keyword of keywords[intent]) {
        if (normalized.includes(keyword)) {
          scores[intent]++;
        }
      }
    }

    const detectedIntent = (Object.keys(scores) as Intent[]).reduce(
      (best, current) => (scores[current] > scores[best] ? current : best),
    );

    if (scores[detectedIntent] === 0) {
      return {
        intent: "general",
        confidence: "LOW",
      };
    }

    return {
      intent: detectedIntent,
      confidence: scores[detectedIntent] >= 2 ? "HIGH" : "MEDIUM",
    };
  }
}
