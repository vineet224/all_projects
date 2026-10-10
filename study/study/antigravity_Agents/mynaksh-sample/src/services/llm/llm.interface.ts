export interface LlmRequest {
  systemPrompt: string;
  userPrompt: string;
  temperature?: number;
  maxOutputTokens?: number;
}

export interface LlmResponse {
  content: string;
  provider: string;
  tokensUsed?: {
    prompt: number;
    completion: number;
  };
}

export interface ILlmProvider {
  name: string;
  generate(req: LlmRequest): Promise<LlmResponse>;
}
