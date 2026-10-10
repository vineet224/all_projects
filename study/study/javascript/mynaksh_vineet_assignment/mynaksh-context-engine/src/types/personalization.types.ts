import { Kundli } from "./kundli.types";
import { Horoscope } from "./horoscope.types";
import { Panchang } from "./panchang.types";
import { UserProfile } from "./user.types";

export type Intent =
  | "career"
  | "relationship"
  | "health"
  | "finance"
  | "general";

export type Confidence = "HIGH" | "MEDIUM" | "LOW";

export type ContextKey =
  | "careerHoroscope"
  | "relationshipHoroscope"
  | "healthHoroscope"
  | "financeHoroscope"
  | "house10"
  | "house7"
  | "house6"
  | "moonSign"
  | "currentDasha"
  | "panchang";

export interface ContextValue {
  key: ContextKey;
  label: string;
  value: unknown;
  priority: "primary" | "secondary";
}

export interface IntentResult {
  intent: Intent;
  confidence: Confidence;
}

export interface AggregatedContext {
  user?: UserProfile;
  kundli?: Kundli;
  horoscope?: Horoscope;
  panchang?: Panchang;
  failures: string[];
}

export interface PersonalizationDecision {
  intent: Intent;
  language: string;
  tone: string;
  maxWords: number;
  selectedContext: ContextValue[];
  excludedContext: ContextKey[];
  confidence: Confidence;
}

export interface PersonalizeRequest {
  userId: string;
  question: string;
}

export interface PersonalizeResponse {
  answer: string;
  confidence: Confidence;
  sourcesUsed: string[];
}
