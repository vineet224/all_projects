export interface BirthDetails {
  date: string; // YYYY-MM-DD
  time: string; // HH:mm
  place: string;
}

export interface UserProfile {
  id: string;
  name: string;
  language: string;
  subscription: "free" | "premium" | "vip";
  tonePreference: string;
  birthDetails: BirthDetails;
}

export interface HouseDetail {
  lord: string;
  strength: "Strong" | "Average" | "Weak";
}

export interface CurrentDasha {
  mahadasha: string;
  antardasha: string;
}

export interface KundliData {
  lagna: string;
  moonSign: string;
  currentDasha: CurrentDasha;
  houses: Record<string, HouseDetail>; // e.g. "6", "7", "10"
}

export interface HoroscopeData {
  career?: string;
  finance?: string;
  health?: string;
  relationship?: string;
}

export interface PanchangData {
  date: string;
  tithi: string;
  nakshatra: string;
  yoga: string;
  karana: string;
}

export interface RawUpstreamContext {
  user: UserProfile;
  kundli?: KundliData | null;
  horoscope?: HoroscopeData | null;
  panchang?: PanchangData | null;
  errors: {
    serviceName: string;
    error: string;
  }[];
}
