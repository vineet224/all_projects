import { Intent, ContextKey } from "../types/personalization.types";

export interface PersonalizationRule {
  primaryContext: ContextKey[];
  secondaryContext: ContextKey[];
  excludedContext: ContextKey[];
}
// question -> the key intent -> context config ,( change my job effect my marriage ) {carerr, 100 , relathisonship: 100} "{career:{hroscope: abxs,house10: 234 }, relationship:{hroscope: afa, house7:xyz}}"
// question - > [{intentkey,confidence}] [{career, confidence: 60}, {relatshionship, confidence: 80}] - > [career: [primarycontext + secondary ] ,60, relationship: [primarycontext + secondary  ],confidend 80, ...]
// 

// merginglogic{list[Intent, confidence]}{
//   iterate over list:
//   if confidence is 90-100 slect primary + secondary
//   if confidence is 70-90 select primary
//   ..


// }

// final response confidence currently is if we have selected or get the primary context for the intent

// each key in the question we check which intent it belongs to and increase that intent counter, high 

// debug we could response with the context slected confidence
// personalization we can give two confidence socre one is context selected confidence , other is llm output confidence(how con the llm output is based)

export const personalizationRules: Record<Intent, PersonalizationRule> = {
  career//question: {
    primaryContext: ["careerHoroscope", "house10"],

    secondaryContext: ["currentDasha", "panchang"],

    excludedContext: [
      "relationshipHoroscope",
      "healthHoroscope",
      "financeHoroscope",
    ],
  },

  relationship: {
    primaryContext: ["relationshipHoroscope", "house7"],

    secondaryContext: ["moonSign", "currentDasha"],

    excludedContext: ["careerHoroscope", "healthHoroscope", "financeHoroscope"],
  },

  health: {
    primaryContext: ["healthHoroscope", "house6"],

    secondaryContext: ["moonSign", "panchang"],

    excludedContext: [
      "careerHoroscope",
      "relationshipHoroscope",
      "financeHoroscope",
    ],
  },

  finance: {
    primaryContext: ["financeHoroscope"],

    secondaryContext: ["currentDasha", "house10"],

    excludedContext: ["relationshipHoroscope", "healthHoroscope"],
  },

  general: {
    primaryContext: [
      "careerHoroscope",
      "relationshipHoroscope",
      "healthHoroscope",
      "financeHoroscope",
    ],

    secondaryContext: ["moonSign", "currentDasha", "panchang"],

    excludedContext: [],
  },
};
