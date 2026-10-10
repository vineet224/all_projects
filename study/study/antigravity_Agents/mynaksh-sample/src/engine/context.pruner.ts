import { RawUpstreamContext } from "../types/domain";
import {
  ContextKey,
  ExtractedContextItem,
  PersonalizationRule,
} from "../types/personalization";

export interface PrunedContextResult {
  selectedContext: string[];
  excludedContext: string[];
  extractedItems: ExtractedContextItem[];
  availableKeySet: Set<ContextKey>;
  formattedContextText: string;
}

export class ContextPruner {
  /**
   * Resolves a ContextKey from raw upstream data. Returns null if data is missing/failed.
   */
  private resolveSourceValue(
    key: ContextKey,
    raw: RawUpstreamContext,
  ): string | null {
    switch (key) {
      case "kundli.houses.10": {
        const house = raw.kundli?.houses?.["10"];
        if (!house) return null;
        return `10th House (Karma/Career): Lord is ${house.lord}, Strength is ${house.strength}.`;
      }
      case "kundli.houses.7": {
        const house = raw.kundli?.houses?.["7"];
        if (!house) return null;
        return `7th House (Relationships/Partnership): Lord is ${house.lord}, Strength is ${house.strength}.`;
      }
      case "kundli.houses.6": {
        const house = raw.kundli?.houses?.["6"];
        if (!house) return null;
        return `6th House (Health/Daily Routine): Lord is ${house.lord}, Strength is ${house.strength}.`;
      }
      case "kundli.houses.2": {
        const house = raw.kundli?.houses?.["2"];
        if (!house) return null;
        return `2nd House (Wealth & Accumulated Assets): Lord is ${house.lord}, Strength is ${house.strength}.`;
      }
      case "kundli.dasha": {
        const dasha = raw.kundli?.currentDasha;
        if (!dasha) return null;
        return `Current Dasha: Mahadasha of ${dasha.mahadasha}, Antardasha of ${dasha.antardasha}.`;
      }
      case "kundli.moonSign": {
        if (!raw.kundli?.moonSign) return null;
        return `Moon Sign (Rashi): ${raw.kundli.moonSign}.`;
      }
      case "kundli.lagna": {
        if (!raw.kundli?.lagna) return null;
        return `Ascendant (Lagna): ${raw.kundli.lagna}.`;
      }
      case "horoscope.career": {
        if (!raw.horoscope?.career) return null;
        return `Career Horoscope: "${raw.horoscope.career}"`;
      }
      case "horoscope.relationship": {
        if (!raw.horoscope?.relationship) return null;
        return `Relationship Horoscope: "${raw.horoscope.relationship}"`;
      }
      case "horoscope.health": {
        if (!raw.horoscope?.health) return null;
        return `Health Horoscope: "${raw.horoscope.health}"`;
      }
      case "horoscope.finance": {
        if (!raw.horoscope?.finance) return null;
        return `Finance Horoscope: "${raw.horoscope.finance}"`;
      }
      case "panchang": {
        if (!raw.panchang) return null;
        return `Today's Panchang: Date: ${raw.panchang.date}, Tithi: ${raw.panchang.tithi}, Nakshatra: ${raw.panchang.nakshatra}, Yoga: ${raw.panchang.yoga}.`;
      }
      case "user.profile": {
        return `User: ${raw.user.name}, Birth Date: ${raw.user.birthDetails.date}, Place: ${raw.user.birthDetails.place}.`;
      }
      default:
        return null;
    }
  }

  prune(
    rule: PersonalizationRule,
    raw: RawUpstreamContext,
  ): PrunedContextResult {
    const selectedLabels: string[] = [];
    const extractedItems: ExtractedContextItem[] = [];
    const availableKeySet = new Set<ContextKey>();

    // 1. Process Primary Sources
    for (const req of rule.primarySources) {
      const val = this.resolveSourceValue(req.key, raw);
      if (val) {
        selectedLabels.push(req.label);
        availableKeySet.add(req.key);
        extractedItems.push({
          key: req.key,
          label: req.label,
          category: "primary",
          content: val,
        });
      }
    }

    // 2. Process Secondary Sources
    for (const req of rule.secondarySources) {
      const val = this.resolveSourceValue(req.key, raw);
      if (val) {
        selectedLabels.push(req.label);
        availableKeySet.add(req.key);
        extractedItems.push({
          key: req.key,
          label: req.label,
          category: "secondary",
          content: val,
        });
      }
    }

    // 3. Formatted Context String for the LLM
    const formattedContextText = extractedItems
      .map((item) => `- [${item.label}]: ${item.content}`)
      .join("\n");

    return {
      selectedContext: selectedLabels,
      excludedContext: rule.excludedSources,
      extractedItems,
      availableKeySet,
      formattedContextText,
    };
  }
}
