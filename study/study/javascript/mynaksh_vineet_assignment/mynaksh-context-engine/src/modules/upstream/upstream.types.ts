import { UserAdapter } from "./user.adapter";
import { KundliAdapter } from "./kundli.adapter";
import { HoroscopeAdapter } from "./horoscope.adapter";
import { PanchangAdapter } from "./panchang.adapter";

export interface UpstreamDependencies {
  userAdapter: UserAdapter;
  kundliAdapter: KundliAdapter;
  horoscopeAdapter: HoroscopeAdapter;
  panchangAdapter: PanchangAdapter;
}
