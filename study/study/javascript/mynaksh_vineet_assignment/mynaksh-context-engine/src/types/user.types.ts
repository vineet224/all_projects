export type Language = "en" | "hi";

export type TonePreference =
  | "motivational"
  | "professional"
  | "friendly"
  | "concise";

export type Subscription = "free" | "premium";

export interface BirthDetails {
  date: string;
  time: string;
  place: string;
}

export interface UserProfile {
  id: string;
  name: string;
  language: Language;
  subscription: Subscription;
  tonePreference: TonePreference;
  birthDetails: BirthDetails;
}
