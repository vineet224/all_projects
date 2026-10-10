import { LLMGenerateOptions, LLMGenerateResult } from "../../types/llm.types";

export interface LLMProvider {
  generate(
    prompt: string,
    options: LLMGenerateOptions,
  ): Promise<LLMGenerateResult>;
}
