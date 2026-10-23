"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/AuthProvider";
import { FullPageLoading } from "@/components/layout/FullPage";

// The root address just sends people to the right place.
export default function Home() {
  const { state } = useAuth();
  const router = useRouter();
  useEffect(() => {
    if (state.status === "ready") router.replace("/dashboard/");
    else if (state.status === "signed-out") router.replace("/login/");
    else if (state.status === "denied") router.replace("/access-denied/");
  }, [state.status, router]);
  return <FullPageLoading />;
}
