import { UserProfile } from "../types/domain";
import {
  PersonalizationDecision,
  PersonalizationRule,
} from "../types/personalization";

export interface BuiltPrompt {
  systemPrompt: string;
  userPrompt: string;
}

export class PromptBuilder {
  build(
    rule: PersonalizationRule,
    decision: PersonalizationDecision,
    user: UserProfile,
    question: string,
  ): BuiltPrompt {
    const languageMap: Record<string, string> = {
      en: "English",
      hi: "Hindi",
      es: "Spanish",
    };

    const targetLanguage =
      languageMap[user.language] || decision.language || "English";
    const targetTone =
      user.tonePreference || decision.tone || "Empathetic and Insightful";

    let degradationNotice = "";
    if (decision.isDegraded) {
      degradationNotice = `\nNOTE ON DATA AVAILABILITY: ${decision.degradationReason || "Certain astrological indicators are temporarily unavailable. Provide a grounded answer based on available context with appropriate modesty."}`;
    }

    const systemPrompt = `You are MyNaksh AI, an expert Vedic Astrological Advisor.
Your mission is to provide personalized, grounded, and empowering astrological guidance based strictly on verified Vedic parameters.

GUIDELINES:
1. Tone: ${targetTone}.
2. Response Language: ${targetLanguage}.
3. Length Constraint: Keep your response under approximately ${decision.maxWords} words. Be concise, actionable, and structured.
4. Grounding: Rely ONLY on the provided Astrological Context. Do NOT fabricate houses, dashas, or planetary alignments not listed in the context.
5. Domain Focus: ${rule.systemDirective}${degradationNotice}
6. Do NOT mention other unrelated domains (e.g. do not discuss romantic relationships if the question is strictly about career).`;

    const userPrompt = `User Question: "${question}"

Astrological Context (Filtered & Grounded):
${decision.promptContextText || "(No specific birth chart context available; provide general Vedic principles)"}

Please provide personalized astrological guidance for ${user.name}:`;

    return {
      systemPrompt,
      userPrompt,
    };
  }
}
