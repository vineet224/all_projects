import { ILlmProvider, LlmRequest, LlmResponse } from "./llm.interface";

export class MockLlmProvider implements ILlmProvider {
  name = "MockLlmProvider";

  async generate(req: LlmRequest): Promise<LlmResponse> {
    // Simulate realistic inference latency (120ms)
    await new Promise((r) => setTimeout(r, 120));

    let content = "";

    if (
      req.userPrompt.toLowerCase().includes("job") ||
      req.userPrompt.toLowerCase().includes("career")
    ) {
      content =
        "Based on your 10th House alignment with Moon in a strong position, combined with your Career Horoscope highlighting new networking opportunities, the upcoming period is highly favorable for professional exploration. However, under your current Rahu-Mars dasha, ensure you thoroughly evaluate contractual terms before finalizing any major career transitions. Focus on networking strategically over the next two to three months.";
    } else if (
      req.userPrompt.toLowerCase().includes("relationship") ||
      req.userPrompt.toLowerCase().includes("partner")
    ) {
      content =
        "Your 7th House highlights the need for mindful communication, as Mars indicates passionate yet sensitive dialogues. Your current transit indicates that active listening and emotional vulnerability will strengthen your bond. Prioritize calm, transparent conversations with your partner to navigate any lingering misunderstandings.";
    } else if (
      req.userPrompt.toLowerCase().includes("health") ||
      req.userPrompt.toLowerCase().includes("sleep")
    ) {
      content =
        "Looking at your 6th House governed by Jupiter and your health horoscope, your primary focus should be restoring circadian rhythm and consistent rest. Mental vitality is supported, but physical endurance requires grounding routines and adequate hydration. Avoid erratic schedules and embrace gentle mindfulness.";
    } else {
      content =
        "Reflecting on your planetary configuration and today's auspicious Siddhi yoga in Rohini nakshatra, this is a time for steady, deliberate actions rather than rushed decisions. Maintain alignment with your long-term intentions, stay focused on self-discipline, and allow current cosmic currents to guide your next steps with patience.";
    }

    // Estimate token counts
    const promptTokens = Math.ceil(
      (req.systemPrompt.length + req.userPrompt.length) / 4,
    );
    const completionTokens = Math.ceil(content.length / 4);

    return {
      content,
      provider: "mock-llm",
      tokensUsed: {
        prompt: promptTokens,
        completion: completionTokens,
      },
    };
  }
}
