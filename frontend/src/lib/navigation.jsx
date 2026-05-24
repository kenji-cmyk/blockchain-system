import { Gauge, Network, Pickaxe, Search, WalletCards } from "lucide-react";

const navItems = [
  { id: "dashboard", label: "Dashboard", icon: Gauge },
  { id: "explorer", label: "Explorer", icon: Search },
  { id: "wallet", label: "Wallet", icon: WalletCards },
  { id: "mining", label: "Mining", icon: Pickaxe },
  { id: "peers", label: "Peers", icon: Network }
];

export { navItems };
