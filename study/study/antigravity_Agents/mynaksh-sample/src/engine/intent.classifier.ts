import { Intent } from "../types/personalization";

export interface IIntentClassifier {
  classify(question: string): Intent;
}

export class KeywordIntentClassifier implements IIntentClassifier {
  private rules: { intent: Intent; patterns: RegExp[] }[] = [
    {
      intent: "career",
      patterns: [
        /\b(job|career|work|profession|promotion|boss|company|switch|interview|hire|resignation|salary|business)\b/i,
        /changing my job/i,
        /career growth/i,
        /new job/i,
        /workplace/i,
      ],
    },
    {
      intent: "relationship",
      patterns: [
        /\b(relationship|love|marriage|partner|spouse|dating|marry|divorce|breakup|boyfriend|girlfriend|husband|wife)\b/i,
        /look for my relationship/i,
        /love life/i,
        /marital/i,
      ],
    },
    {
      intent: "health",
      patterns: [
        /\b(health|wellness|fitness|sleep|illness|disease|diet|exercise|mental|stress|healing|energy)\b/i,
        /focus on for my health/i,
        /feeling tired/i,
        /wellbeing/i,
      ],
    },
    {
      intent: "finance",
      patterns: [
        /\b(money|finance|investment|wealth|stock|crypto|debt|loan|property|savings|expenditure|loss|profit)\b/i,
        /financial/i,
        /risky investments/i,
      ],
    },
    {
      intent: "general",
      patterns: [
        /\b(today|week|month|year|general|summarize|guidance|overview|prioritize)\b/i,
        /summarize today's guidance/i,
        /what should i prioritize/i,
        /how does today look/i,
      ],
    },
  ];

  classify(question: string): Intent {
    const cleaned = question.trim().toLowerCase();

    for (const rule of this.rules) {
      for (const pattern of rule.patterns) {
        if (pattern.test(cleaned)) {
          return rule.intent;
        }
      }
    }

    return "general";
  }
}
