import {
  AggregatedContext,
  PersonalizationDecision,
  ContextValue,
} from "../../types/personalization.types";

export class PromptBuilder {
  build(
    question: string,
    decision: PersonalizationDecision,
    context: AggregatedContext,
  ): string {
    const contextLines: string[] = [];

    for (const source of decision.selectedContext) {
      contextLines.push(`${source.label}: ${JSON.stringify(source.value)}`);
    }

    return `
You are a personalized astrology assistant.

Answer the user's question using only the provided context.

Do not invent astrological facts.
Do not use excluded context.
If relevant information is missing, acknowledge the limitation.

Language: ${decision.language}
Tone: ${decision.tone}
Maximum words: ${decision.maxWords}

User Question:
${question}

Selected Context:
${contextLines.join("\n")}

Excluded Context:
${decision.excludedContext.join(", ")} // Excluded context keys only not values

Provide a clear, useful, grounded response.
`.trim();
  }
}
