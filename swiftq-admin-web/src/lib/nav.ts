import type { IconName } from "@/components/ui/Icon";
import type { Section } from "@/lib/auth/permissions";

export interface NavItem {
  section: Section;
  href: string;
  label: string;
  icon: IconName;
  description: string;
}

// One list drives both the sidebar and the dashboard cards.
export const NAV_ITEMS: NavItem[] = [
  { section: "dashboard", href: "/dashboard/", label: "Home", icon: "home", description: "Start here" },
  { section: "staff", href: "/staff/", label: "Staff accounts", icon: "users", description: "Add staff, change roles and clinics, and deactivate accounts." },
  { section: "clinics", href: "/clinics/", label: "Clinics", icon: "clinic", description: "Set up clinics, opening hours and service points." },
  { section: "queue-config", href: "/queue-config/", label: "Queue settings", icon: "sliders", description: "Tune priority weights, jump limits and wait time estimates." },
  { section: "sync", href: "/sync/", label: "Sync log", icon: "sync", description: "Review sync conflicts and sync attempts from clinic devices." },
  { section: "audit", href: "/audit-log/", label: "Audit log", icon: "audit", description: "Not available yet. The server team is still building it." },
  { section: "analytics", href: "/analytics/", label: "Analytics", icon: "chart", description: "Check waiting times and busy hours, and export a CSV." },
];
