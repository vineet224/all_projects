import { Confidence } from "./personalization.types";

export interface LLMGenerateOptions {
  maxWords: number;
  temperature: number;
}

export interface LLMGenerateResult {
  answer: string;
  confidence: Confidence;
}

export interface LLMProvider {
  generate(
    prompt: string,
    options: LLMGenerateOptions,
  ): Promise<LLMGenerateResult>;
}
