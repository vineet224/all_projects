import { z } from "zod";

export const PersonalizeRequestSchema = z.object({
  userId: z.string().min(1, "userId is required"),
  question: z.string().min(3, "question must be at least 3 characters long"),
  providerOverride: z.enum(["mock", "gemini"]).optional(),
});

export type PersonalizeRequest = z.infer<typeof PersonalizeRequestSchema>;

export interface PersonalizeResponse {
  answer: string;
  confidence: "HIGH" | "MEDIUM" | "LOW";
  sourcesUsed: string[];
  metadata?: {
    intent: string;
    latencyMs: number;
    provider: string;
    isDegraded: boolean;
  };
}

export interface DebugPersonalizationResponse {
  intent: string;
  selectedContext: string[];
  excludedContext: string[];
  language: string;
  tone: string;
  maxWords: number;
  confidence: "HIGH" | "MEDIUM" | "LOW";
  isDegraded: boolean;
  degradationReason?: string;
  assembledPrompt: {
    systemDirective: string;
    contextText: string;
  };
}
