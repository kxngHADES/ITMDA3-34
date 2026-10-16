"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import type { ReactNode } from "react";
import { Icon } from "@/components/ui/Icon";
import { Button } from "@/components/ui/Button";
import { useAuth } from "@/lib/auth/AuthProvider";
import { ROLE_LABEL, canAccess } from "@/lib/auth/permissions";
import { NAV_ITEMS } from "@/lib/nav";
import type { Me } from "@/lib/api/types";

export function Shell({ me, children }: { me: Me; children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { signOut } = useAuth();

  // Show only the sections this role may open (API contract section 3).
  const items = NAV_ITEMS.filter((item) => canAccess(me.role, item.section));

  async function handleSignOut() {
    await signOut();
    router.replace("/login/"); // replace, so Back can't return to a signed-in screen
  }

  return (
    <div className="shell">
      <a className="skip-link" href="#main">Skip to content</a>
      <aside className="sidebar">
        <div className="wordmark">
          SwiftQ
          <small>Admin</small>
        </div>
        <nav className="nav" aria-label="Main">
          {items.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className="nav-link"
              aria-current={pathname.startsWith(item.href) ? "page" : undefined}
            >
              <Icon name={item.icon} />
              {item.label}
            </Link>
          ))}
        </nav>
        <div className="sidebar-foot">
          <div>
            <div className="primary" style={{ fontWeight: 600 }}>{me.display_name ?? "Signed in"}</div>
            <div className="muted" style={{ fontSize: "var(--sq-text-footnote-size)" }}>{ROLE_LABEL[me.role]}</div>
          </div>
          <Button onClick={handleSignOut}>
            <Icon name="logout" />
            Sign out
          </Button>
        </div>
      </aside>
      <main id="main" className="main" tabIndex={-1}>
        {children}
      </main>
    </div>
  );
}
