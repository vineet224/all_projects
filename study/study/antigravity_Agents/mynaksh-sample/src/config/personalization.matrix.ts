import { Intent, PersonalizationRule } from "../types/personalization";

/**
 * Declarative Personalization Matrix
 * Defines how different user intents map to primary astrological data,
 * secondary supporting context, and explicitly excluded context (to reduce noise & hallucination).
 */
export const PERSONALIZATION_MATRIX: Record<Intent, PersonalizationRule> = {
  career: {
    intent: "career",
    primarySources: [
      {
        key: "kundli.houses.10",
        label: "10th House (Karma/Profession)",
        isMandatory: true,
      },
      { key: "horoscope.career", label: "Career Horoscope", isMandatory: true },
    ],
    secondarySources: [
      { key: "kundli.dasha", label: "Current Dasha", isMandatory: false },
      { key: "panchang", label: "Today's Panchang", isMandatory: false },
    ],
    excludedSources: ["Relationship Horoscope", "7th House (Partnerships)"],
    defaultMaxWords: 250,
    systemDirective:
      "Focus deeply on professional timing, career growth, vocational strengths, and dasha implications. Maintain clarity without astrological jargon overload.",
  },

  relationship: {
    intent: "relationship",
    primarySources: [
      {
        key: "kundli.houses.7",
        label: "7th House (Relationships/Marriage)",
        isMandatory: true,
      },
      {
        key: "horoscope.relationship",
        label: "Relationship Horoscope",
        isMandatory: true,
      },
    ],
    secondarySources: [
      {
        key: "kundli.moonSign",
        label: "Moon Sign (Chandra Rashi)",
        isMandatory: false,
      },
      { key: "kundli.dasha", label: "Current Dasha", isMandatory: false },
    ],
    excludedSources: ["Career Horoscope", "10th House (Karma/Profession)"],
    defaultMaxWords: 250,
    systemDirective:
      "Provide compassionate, emotionally intelligent guidance regarding romantic partnerships, communication, and emotional harmony based on lunar and planetary alignments.",
  },

  health: {
    intent: "health",
    primarySources: [
      {
        key: "kundli.houses.6",
        label: "6th House (Health/Vitality)",
        isMandatory: true,
      },
      { key: "horoscope.health", label: "Health Horoscope", isMandatory: true },
    ],
    secondarySources: [
      {
        key: "kundli.moonSign",
        label: "Moon Sign (Mental Wellbeing)",
        isMandatory: false,
      },
      { key: "panchang", label: "Today's Panchang", isMandatory: false },
    ],
    excludedSources: ["Finance Horoscope", "2nd House (Wealth)"],
    defaultMaxWords: 220,
    systemDirective:
      "Emphasize daily vitality, holistic balance, stress management, and preventative routines. Always frame insights as astrological wellbeing, never medical diagnosis.",
  },

  finance: {
    intent: "finance",
    primarySources: [
      {
        key: "kundli.houses.2",
        label: "2nd House (Wealth & Assets)",
        isMandatory: false,
      },
      {
        key: "horoscope.finance",
        label: "Finance Horoscope",
        isMandatory: true,
      },
    ],
    secondarySources: [
      { key: "kundli.dasha", label: "Current Dasha", isMandatory: false },
      { key: "kundli.moonSign", label: "Moon Sign", isMandatory: false },
      { key: "panchang", label: "Today's Panchang", isMandatory: false },
    ],
    excludedSources: ["Relationship Horoscope"],
    defaultMaxWords: 220,
    systemDirective:
      "Focus on monetary discipline, investment prudence, and auspicious planetary timing for wealth preservation.",
  },

  general: {
    intent: "general",
    primarySources: [
      { key: "kundli.lagna", label: "Ascendant (Lagna)", isMandatory: false },
      {
        key: "kundli.moonSign",
        label: "Moon Sign (Rashi)",
        isMandatory: false,
      },
      { key: "kundli.dasha", label: "Current Dasha", isMandatory: false },
    ],
    secondarySources: [
      { key: "panchang", label: "Today's Panchang", isMandatory: false },
      { key: "horoscope.career", label: "Career Snapshot", isMandatory: false },
      {
        key: "horoscope.relationship",
        label: "Relationship Snapshot",
        isMandatory: false,
      },
      { key: "horoscope.health", label: "Health Snapshot", isMandatory: false },
    ],
    excludedSources: [],
    defaultMaxWords: 250,
    systemDirective:
      "Provide a holistic, balanced overview covering spiritual alignment, general energies of the day, and overarching dasha themes.",
  },
};
