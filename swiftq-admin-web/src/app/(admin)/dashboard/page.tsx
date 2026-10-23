"use client";

import Link from "next/link";
import { useMe } from "@/lib/auth/AuthProvider";
import { ROLE_LABEL, canAccess } from "@/lib/auth/permissions";
import { NAV_ITEMS } from "@/lib/nav";
import { Icon } from "@/components/ui/Icon";

export default function DashboardPage() {
  const me = useMe();
  const items = NAV_ITEMS.filter((i) => i.section !== "dashboard" && canAccess(me.role, i.section));
  return (
    <>
      <div className="page-head">
        <div>
          <h1>Welcome{me.display_name ? `, ${me.display_name}` : ""}</h1>
          <p>You are signed in as {ROLE_LABEL[me.role]}. Here is what you can do.</p>
        </div>
      </div>
      <div className="card-grid">
        {items.map((item) => (
          <Link key={item.href} href={item.href} className="card card-link">
            <span className="icon-tile"><Icon name={item.icon} /></span>
            <h2>{item.label}</h2>
            <p className="muted">{item.description}</p>
          </Link>
        ))}
      </div>
    </>
  );
}
