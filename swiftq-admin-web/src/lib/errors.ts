import { ApiError } from "@/lib/api/client";

/** The text to show a person. ApiError messages are already plain language (contract 2.3). */
export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) return e.message;
  return "Something went wrong. Try again.";
}
