export interface Dasha {
  mahadasha: string;
  antardasha: string;
}

export interface House {
  lord: string;
  strength: string;
}

export interface Kundli {
  lagna: string;
  moonSign: string;
  currentDasha: Dasha;
  houses: Record<string, House>;
}
