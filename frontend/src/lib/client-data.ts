import { api } from "@/lib/api";
import type { CardBatchResponse, CardSummary } from "@/lib/contracts";

export async function hydrateCards(cardIds: string[]) {
  const unique = [...new Set(cardIds.filter(Boolean))];
  const batches: string[][] = [];
  for (let index = 0; index < unique.length; index += 100) {
    batches.push(unique.slice(index, index + 100));
  }
  const responses = await Promise.all(
    batches.map((ids) =>
      api<CardBatchResponse>("/cards/batch", {
        method: "POST",
        body: JSON.stringify({ cardIds: ids }),
      }),
    ),
  );
  return new Map<string, CardSummary>(
    responses.flatMap((response) => response.cards).map((card) => [card.id, card]),
  );
}

export function money(value: string) {
  const amount = Number(value);
  if (!Number.isFinite(amount)) return value || "—";
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 2,
  }).format(amount);
}

export function dateRange(start: string, end: string) {
  const startDate = new Date(`${start}T12:00:00`);
  const endDate = new Date(`${end}T12:00:00`);
  if (Number.isNaN(startDate.getTime()) || Number.isNaN(endDate.getTime())) return `${start} – ${end}`;
  const format = new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", year: "numeric" });
  if (start === end) return format.format(startDate);
  const sameYear = startDate.getFullYear() === endDate.getFullYear();
  if (!sameYear) return `${format.format(startDate)}–${format.format(endDate)}`;
  const startText = startDate.toLocaleDateString("en-US", { month: "short", day: "numeric" });
  const endText = startDate.getMonth() === endDate.getMonth()
    ? String(endDate.getDate())
    : endDate.toLocaleDateString("en-US", { month: "short", day: "numeric" });
  return `${startText}–${endText}, ${endDate.getFullYear()}`;
}

export function relativeTime(epochSeconds: number) {
  const seconds = Math.max(0, Math.floor(Date.now() / 1000) - epochSeconds);
  if (seconds < 60) return "now";
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m`;
  if (seconds < 86_400) return `${Math.floor(seconds / 3600)}h`;
  return `${Math.floor(seconds / 86_400)}d`;
}

export function initials(value: string) {
  const parts = value.trim().split(/\s+/).filter(Boolean);
  return (parts.slice(0, 2).map((part) => part[0]).join("") || "V").toUpperCase();
}

export function conditionLabel(value: string) {
  return value === "dmg" ? "DMG" : value.toUpperCase();
}
