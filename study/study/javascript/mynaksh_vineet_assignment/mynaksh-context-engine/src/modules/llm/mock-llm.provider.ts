import { LLMGenerateOptions, LLMGenerateResult } from "../../types/llm.types";

import { LLMProvider } from "./llm.provider";

export class MockLLMProvider implements LLMProvider {
  async generate(
    prompt: string,
    _options: LLMGenerateOptions,
  ): Promise<LLMGenerateResult> {
    const lowerPrompt = prompt.toLowerCase();

    let answer =
      "Based on the available astrological context, consider reflecting on your current priorities and making thoughtful decisions.";

    if (lowerPrompt.includes("career horoscope")) {
      answer =
        "Your career horoscope suggests that networking may bring new opportunities. The strong 10th House indicates a supportive professional foundation. Consider exploring suitable opportunities while evaluating your current responsibilities carefully.";
    } else if (lowerPrompt.includes("relationship horoscope")) {
      answer =
        "Your relationship horoscope suggests that communication with your partner may improve. Focus on honest conversations, patience, and understanding each other's expectations.";
    } else if (lowerPrompt.includes("health horoscope")) {
      answer =
        "Your health horoscope suggests prioritizing proper sleep and maintaining a balanced routine. Focus on consistent habits and avoid neglecting rest.";
    } else if (lowerPrompt.includes("finance horoscope")) {
      answer =
        "Your finance horoscope suggests avoiding risky investments. Focus on budgeting, careful planning, and understanding the risks before making financial decisions.";
    }

    return {
      answer,
      confidence: "HIGH",
    };
  }
}
