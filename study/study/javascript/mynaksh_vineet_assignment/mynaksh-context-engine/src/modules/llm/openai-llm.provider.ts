import OpenAI from "openai";

import { LLMGenerateOptions, LLMGenerateResult } from "../../types/llm.types";

import { LLMProvider } from "./llm.provider";

export class OpenAILLMProvider implements LLMProvider {
  private readonly client: OpenAI;

  constructor(
    apiKey: string,
    private readonly model: string,
  ) {
    this.client = new OpenAI({
      apiKey,
    });
  }

  async generate(
    prompt: string,
    options: LLMGenerateOptions,
  ): Promise<LLMGenerateResult> {
    const response = await this.client.chat.completions.create({
      model: this.model,

      messages: [
        {
          role: "system",
          content:
            "You are a personalized astrology assistant. Answer only using the provided context.",
        },

        {
          role: "user",
          content: prompt,
        },
      ],

      temperature: options.temperature,

      max_tokens: Math.ceil(options.maxWords * 1.5),
    });

    const answer =
      response.choices[0]?.message?.content ?? "Unable to generate a response.";

    return {
      answer,
      confidence: "HIGH",
    };
  }
}
