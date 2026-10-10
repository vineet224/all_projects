import {
  AggregatedContext,
  ContextKey,
} from "../../types/personalization.types";

type ContextResolver = (context: AggregatedContext) => unknown;

interface ContextDefinition {
  label: string;
  resolve: ContextResolver;
}

export const contextDefinitions: Record<ContextKey, ContextDefinition> = {
  careerHoroscope: {
    label: "Career Horoscope",
    resolve: (context) => context.horoscope?.career, // (upstreamvalue) create a speratre aggreatedcontext{}
  },

  relationshipHoroscope: {
    label: "Relationship Horoscope",
    resolve: (context) => context.horoscope?.relationship,
  },

  healthHoroscope: {
    label: "Health Horoscope",
    resolve: (context) => context.horoscope?.health,
  },

  financeHoroscope: {
    label: "Finance Horoscope",
    resolve: (context) => context.horoscope?.finance,
  },

  house10: {
    label: "10th House",
    resolve: (context) => context.kundli?.houses?.["10"],
  },

  house7: {
    label: "7th House",
    resolve: (context) => context.kundli?.houses?.["7"],
  },

  house6: {
    label: "6th House",
    resolve: (context) => context.kundli?.houses?.["6"],
  },

  moonSign: {
    label: "Moon Sign",
    resolve: (context) => context.kundli?.moonSign,
  },

  currentDasha: {
    label: "Current Dasha",
    resolve: (context) => context.kundli?.currentDasha,
  },

  panchang: {
    label: "Today's Panchang",
    resolve: (context) => context.panchang,
  },
};
