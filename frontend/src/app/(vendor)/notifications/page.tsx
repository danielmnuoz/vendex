"use client";

import Link from "next/link";
import { CheckCheck, SlidersHorizontal } from "lucide-react";
import { useState } from "react";

import { CardIdentity, EmptyState, ErrorState, LoadingState, PageHeader, StatusPill } from "@/components/primitives";
import { useResource } from "@/hooks/use-resource";
import { api, messageFor } from "@/lib/api";
import { hydrateCards, relativeTime } from "@/lib/client-data";
import type { CardSummary, NotificationItem, PageResponse } from "@/lib/contracts";

async function loadNotifications() {
  const page = await api<PageResponse<NotificationItem>>("/notifications?pageSize=100");
  return { page, cards: await hydrateCards(page.items.map((item) => item.cardId)) as Map<string, CardSummary> };
}

export default function NotificationsPage() {
  const resource = useResource(loadNotifications, []);
  const [error, setError] = useState("");
  if (resource.loading) return <LoadingState label="Loading notifications…" />;
  if (resource.error || !resource.data) return <ErrorState error={resource.error} onRetry={resource.reload} />;
  const data = resource.data;

  async function markRead(item: NotificationItem) {
    setError("");
    try { await api<NotificationItem>(`/notifications/${item.id}/read`, { method: "PATCH" }); resource.reload(); } catch (reason) { setError(messageFor(reason)); }
  }

  return <><PageHeader eyebrow="Activity" title="Notifications" description="Overlap changes and saved-plan updates arrive here without exposing private contact details." action={<Link className="button secondary icon-label" href="/settings"><SlidersHorizontal size={17} /> Preferences</Link>} />{error ? <p className="form-error">{error}</p> : null}{data.page.items.length === 0 ? <EmptyState title="You are all caught up" description="New event-scoped overlap activity will appear here." /> : <section className="panel feed-panel">{data.page.items.map((item) => <article className={`feed-item${item.read ? " read" : ""}`} key={item.id}><span className="notification-mark" /><CardIdentity card={data.cards.get(item.cardId)} compact /><div className="feed-copy"><div><StatusPill tone={item.active ? "blue" : "neutral"}>{item.trigger.replaceAll("_", " ")}</StatusPill><time>{relativeTime(item.createdAtEpochSeconds)} ago</time></div><p>{item.counterparty?.shopName ? `${item.counterparty.shopName} is connected to this update.` : "An event vendor update changed this opportunity."}</p></div>{!item.read ? <button className="row-icon-button" onClick={() => void markRead(item)} aria-label="Mark notification as read"><CheckCheck size={17} /></button> : null}</article>)}</section>}</>;
}
