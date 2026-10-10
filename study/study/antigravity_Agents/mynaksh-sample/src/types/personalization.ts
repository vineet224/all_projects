export type Intent =
  | "career"
  | "relationship"
  | "health"
  | "finance"
  | "general";

export type ContextKey =
  | "kundli.houses.10"
  | "kundli.houses.7"
  | "kundli.houses.6"
  | "kundli.houses.2"
  | "kundli.dasha"
  | "kundli.moonSign"
  | "kundli.lagna"
  | "horoscope.career"
  | "horoscope.relationship"
  | "horoscope.health"
  | "horoscope.finance"
  | "panchang"
  | "user.profile";

export interface ContextRequirement {
  key: ContextKey;
  label: string;
  isMandatory: boolean; // If mandatory source is missing, confidence degrades to LOW
}

export interface PersonalizationRule {
  intent: Intent;
  primarySources: ContextRequirement[];
  secondarySources: ContextRequirement[];
  excludedSources: string[]; // Labels of sources explicitly excluded to avoid noise/hallucination
  defaultMaxWords: number;
  systemDirective: string;
}

export interface ExtractedContextItem {
  key: ContextKey;
  label: string;
  category: "primary" | "secondary";
  content: string;
}

export interface PersonalizationDecision {
  intent: Intent;
  language: string;
  tone: string;
  maxWords: number;
  selectedContext: string[]; // List of source labels included
  excludedContext: string[]; // List of source labels excluded
  availableItems: ExtractedContextItem[];
  confidence: "HIGH" | "MEDIUM" | "LOW";
  promptContextText: string;
  isDegraded: boolean;
  degradationReason?: string;
}
