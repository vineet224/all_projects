import { UserProfile } from "../../types/user.types";
import { Kundli } from "../../types/kundli.types";
import { Horoscope } from "../../types/horoscope.types";
import { Panchang } from "../../types/panchang.types";

// all this data is not typecasted it's pure string data

export const mockUsers: Record<string, UserProfile> = {
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
    name: "Priya Sharma",
    language: "en",
    subscription: "free",
    tonePreference: "concise",
    birthDetails: {
      date: "1998-04-12",
      time: "14:20",
      place: "Mumbai",
    },
  },
};

export const mockKundlis: Record<string, Kundli> = {
  user_101: {
    lagna: "Libra",
    moonSign: "Scorpio",

    currentDasha: {
      mahadasha: "Rahu",
      antardasha: "Mars",
    },

    houses: {
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

  user_102: {
    lagna: "Aries",
    moonSign: "Cancer",

    currentDasha: {
      mahadasha: "Jupiter",
      antardasha: "Venus",
    },

    houses: {
      "6": {
        lord: "Mercury",
        strength: "Strong",
      },

      "7": {
        lord: "Venus",
        strength: "Average",
      },

      "10": {
        lord: "Saturn",
        strength: "Average",
      },
    },
  },
};

export const mockHoroscopes: Record<string, Horoscope> = {
  user_101: {
    career: "Networking may bring new opportunities.",
    finance: "Avoid risky investments.",
    health: "Prioritize proper sleep.",
    relationship: "Communication with your partner improves.",
  },

  user_102: {
    career: "Consistent effort may improve professional stability.",
    finance: "Focus on budgeting and long-term planning.",
    health: "Maintain a balanced routine.",
    relationship: "Open communication can strengthen relationships.",
  },
};

export const mockPanchang: Panchang = {
  date: "2026-08-01",
  tithi: "Shukla Panchami",
  nakshatra: "Rohini",
  yoga: "Siddhi",
  karana: "Bava",
};
