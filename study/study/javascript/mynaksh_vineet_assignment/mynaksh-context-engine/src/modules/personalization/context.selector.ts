import {
  AggregatedContext,
  ContextKey,
  Intent,
  ContextValue,
} from "../../types/personalization.types";

import { personalizationRules } from "../../config/personalization.rules";
import { contextDefinitions } from "./context-resolvers";

export class ContextSelector {
  select(
    intent: Intent,
    context: AggregatedContext,
  ): {
    selectedContext: ContextValue[];
    excludedContext: ContextKey[];
  } {
    const rule = personalizationRules[intent];

    const selectedContext: ContextValue[] = [];
    const seenKeys = new Set<ContextKey>();

    const addContexts = (
      keys: ContextKey[],
      priority: "primary" | "secondary",
    ) => {
      for (const key of keys) {
        if (seenKeys.has(key)) {
          continue;
        }

        seenKeys.add(key);

        const definition = contextDefinitions[key];

        if (!definition) {
          continue;
        }

        const value = definition.resolve(context);

        if (value === undefined || value === null) {
          continue;
        }

        selectedContext.push({
          key,
          label: definition.label,
          value,
          priority,
        });
      }
    };

    addContexts(rule.primaryContext, "primary");
    addContexts(rule.secondaryContext, "secondary");

    return {
      selectedContext,
      excludedContext: rule.excludedContext,
    };
  }
}
