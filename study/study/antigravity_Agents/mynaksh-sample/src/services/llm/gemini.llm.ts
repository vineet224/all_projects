import { GoogleGenAI } from "@google/genai";
import { ILlmProvider, LlmRequest, LlmResponse } from "./llm.interface";

export class GeminiLlmProvider implements ILlmProvider {
  name = "GeminiLlmProvider";
  private ai: GoogleGenAI | null = null;

  constructor(
    private apiKey: string,
    private modelName: string = "gemini-2.5-flash",
  ) {
    if (this.apiKey) {
      this.ai = new GoogleGenAI({ apiKey: this.apiKey });
    }
  }

  async generate(req: LlmRequest): Promise<LlmResponse> {
    if (!this.ai || !this.apiKey) {
      throw new Error(
        "GeminiLlmProvider: GEMINI_API_KEY is not configured. Please supply an API key in .env or switch to mock provider.",
      );
    }

    const response = await this.ai.models.generateContent({
      model: this.modelName,
      contents: req.userPrompt,
      config: {
        systemInstruction: req.systemPrompt,
        temperature: req.temperature ?? 0.7,
      },
    });

    const text = response.text || "";

    return {
      content: text,
      provider: `gemini (${this.modelName})`,
      tokensUsed: {
        prompt: Math.ceil(
          (req.systemPrompt.length + req.userPrompt.length) / 4,
        ),
        completion: Math.ceil(text.length / 4),
      },
    };
  }
}
