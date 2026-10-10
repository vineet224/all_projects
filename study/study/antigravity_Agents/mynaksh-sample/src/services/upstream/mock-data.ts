import {
  HoroscopeData,
  KundliData,
  PanchangData,
  UserProfile,
} from "../../types/domain";

export const MOCK_USERS: Record<string, UserProfile> = {
  user_101: {
    id: "user_101",
    name: "Aarav Sharma",
    language: "en",
    subscription: "premium",
    tonePreference: "motivational",
    birthDetails: {
      date: "1997-08-15",
      time: "09:35",
      place: "Delhi",
    },
  },
  user_102: {
    id: "user_102",
    name: "Priya Patel",
    language: "hi",
    subscription: "free",
    tonePreference: "calm",
    birthDetails: {
      date: "2000-03-22",
      time: "14:20",
      place: "Mumbai",
    },
  },
};

export const MOCK_KUNDLI: Record<string, KundliData> = {
  user_101: {
    lagna: "Libra",
    moonSign: "Scorpio",
    currentDasha: {
      mahadasha: "Rahu",
      antardasha: "Mars",
    },
    houses: {
      "2": {
        lord: "Mars",
        strength: "Average",
      },
      "6": {
        lord: "Jupiter",
        strength: "Average",
      },
      "7": {
        lord: "Mars",
        strength: "Weak",
      },
      "10": {
        lord: "Moon",
        strength: "Strong",
      },
    },
  },
};

export const MOCK_HOROSCOPE: Record<string, HoroscopeData> = {
  user_101: {
    career: "Networking may bring new opportunities.",
    finance: "Avoid risky investments.",
    health: "Prioritize proper sleep.",
    relationship: "Communication with your partner improves.",
  },
};

export const MOCK_PANCHANG: PanchangData = {
  date: "2026-08-01",
  tithi: "Shukla Panchami",
  nakshatra: "Rohini",
  yoga: "Siddhi",
  karana: "Bava",
};
