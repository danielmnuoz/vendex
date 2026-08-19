"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  Bell,
  Boxes,
  CalendarDays,
  Gauge,
  HandCoins,
  LogOut,
  Settings,
  Sparkles,
} from "lucide-react";
import { useEffect, useState, type ReactNode } from "react";

import { api } from "@/lib/api";
import type { Profile, UnreadCount } from "@/lib/contracts";
import { initials } from "@/lib/client-data";

const links = [
  { href: "/dashboard", label: "Overview", icon: Gauge },
  { href: "/inventory", label: "Inventory", icon: Boxes },
  { href: "/buy-list", label: "Buy list", icon: HandCoins },
  { href: "/events", label: "Events", icon: CalendarDays },
  { href: "/overlaps", label: "Matches", icon: Sparkles },
] as const;

function active(pathname: string, href: string) {
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const [profile, setProfile] = useState<Profile | null>(null);
  const [unread, setUnread] = useState(0);

  useEffect(() => {
    Promise.all([
      api<Profile>("/profile"),
      api<UnreadCount>("/notifications/unread-count"),
    ]).then(([nextProfile, count]) => {
      setProfile(nextProfile);
      setUnread(count.unreadCount);
    }).catch(() => undefined);
  }, []);

  async function logout() {
    await fetch("/api/session/logout", { method: "POST" });
    router.replace("/login");
    router.refresh();
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <Link className="brand" href="/dashboard" aria-label="VenDex dashboard">
          <span className="brand-mark" aria-hidden="true">V</span>
          <span>VenDex</span>
        </Link>
        <nav className="desktop-nav" aria-label="Primary navigation">
          {links.map((link) => (
            <Link className={`nav-link${active(pathname, link.href) ? " active" : ""}`} href={link.href} key={link.href}>
              {link.label}
            </Link>
          ))}
        </nav>
        <div className="topbar-actions">
          <Link className="icon-button" href="/notifications" aria-label={`${unread} unread notifications`}>
            <Bell size={17} aria-hidden="true" />
            {unread > 0 ? <span className="notification-count">{Math.min(unread, 99)}</span> : null}
          </Link>
          <Link className="avatar" href="/settings" aria-label="Open account settings">
            {initials(profile?.shopName ?? "Vendor")}
          </Link>
          <button className="logout-button" type="button" onClick={logout} aria-label="Sign out">
            <LogOut size={17} aria-hidden="true" />
          </button>
        </div>
      </header>
      <main className="dashboard">{children}</main>
      <nav className="mobile-nav" aria-label="Mobile navigation">
        {links.slice(0, 4).map((link) => {
          const Icon = link.icon;
          return (
            <Link className={active(pathname, link.href) ? "active" : ""} href={link.href} key={link.href}>
              <Icon size={18} aria-hidden="true" />
              {link.label}
            </Link>
          );
        })}
        <Link className={active(pathname, "/settings") ? "active" : ""} href="/settings">
          <Settings size={18} aria-hidden="true" />
          Account
        </Link>
      </nav>
    </div>
  );
}
