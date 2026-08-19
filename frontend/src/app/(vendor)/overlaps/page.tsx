"use client";

import { Bookmark, LockKeyhole, MapPin, Sparkles } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";

import { CardIdentity, EmptyState, ErrorState, LoadingState, PageHeader, StatusPill } from "@/components/primitives";
import { useResource } from "@/hooks/use-resource";
import { api, messageFor } from "@/lib/api";
import { conditionLabel, hydrateCards, money } from "@/lib/client-data";
import type { EventRegistration, EventSummary, Overlap, PageResponse, Profile, SavedOverlap } from "@/lib/contracts";

async function loadContext() {
  const [profile, events, registrations] = await Promise.all([
    api<Profile>("/profile"),
    api<PageResponse<EventSummary>>("/events?pageSize=100"),
    api<PageResponse<EventRegistration>>("/events/registrations?pageSize=100"),
  ]);
  const registered = new Set(registrations.items.map((item) => item.eventId));
  return { profile, events: events.items.filter((event) => registered.has(event.id)) };
}

async function loadMatches(eventId: string) {
  const [overlaps, saved] = await Promise.all([
    api<PageResponse<Overlap>>(`/overlaps/event/${eventId}?pageSize=100`),
    api<PageResponse<SavedOverlap>>(`/overlaps/event/${eventId}/saved?pageSize=100`),
  ]);
  return { overlaps: overlaps.items, saved: saved.items, cards: await hydrateCards(overlaps.items.map((item) => item.cardId)) };
}

function MatchResults({ eventId, profile }: { eventId: string; profile: Profile }) {
  const resource = useResource(() => loadMatches(eventId), [eventId]);
  const [error, setError] = useState("");
  if (resource.loading) return <LoadingState label="Calculating event opportunities…" />;
  if (resource.error || !resource.data) return <ErrorState error={resource.error} onRetry={resource.reload} />;
  const data = resource.data;
  const savedIds = new Set(data.saved.map((item) => item.overlap.id));

  async function save(overlap: Overlap) {
    setError("");
    try { await api<SavedOverlap>(`/overlaps/${overlap.id}/save`, { method: "POST" }); resource.reload(); } catch (reason) { setError(messageFor(reason)); }
  }

  return <>{error ? <p className="form-error">{error}</p> : null}{data.overlaps.length === 0 ? <EmptyState title="No active overlaps" description="Keep your inventory and buy list current. VenDex recomputes opportunities as registered vendors update theirs." /> : <div className="match-grid">{data.overlaps.map((overlap) => { const buying = overlap.buyerVendorId === profile.userId; const card = data.cards.get(overlap.cardId); const saved = savedIds.has(overlap.id); return <article className="panel match-card" key={overlap.id}><div className="match-card-heading"><StatusPill tone={buying ? "blue" : "warm"}>{buying ? "You can buy" : "You can sell"}</StatusPill>{saved ? <StatusPill><Bookmark size={12} /> Saved</StatusPill> : null}</div><CardIdentity card={card} /><div className="match-values"><span><small>{buying ? "Seller ask" : "Your ask"}</small><strong>{money(overlap.askingPrice)}</strong></span><span><small>{buying ? "Your max" : "Buyer max"}</small><strong>{money(overlap.maxBuyPrice)}</strong></span><span><small>Available / wanted</small><strong>{overlap.availableQuantity} / {overlap.quantityWanted}</strong></span></div><div className="match-counterparty"><span className="avatar small">{overlap.counterparty?.shopName?.slice(0, 2).toUpperCase() || "V"}</span><span><strong>{overlap.counterparty?.shopName ?? "Registered vendor"}</strong><small><MapPin size={12} /> Booth stays private until the reveal workflow ships</small></span></div><div className="match-facts"><span>{conditionLabel(overlap.sellerCondition)} available</span><span>{conditionLabel(overlap.minimumCondition)}+ required</span><span>Score {overlap.score}</span></div><div className="match-actions"><button className="button secondary icon-label" disabled={saved} onClick={() => void save(overlap)}><Bookmark size={16} /> {saved ? "Saved to plan" : "Save to event plan"}</button><button className="button locked icon-label" disabled title="Offer-owned contact reveal is a later backend phase"><LockKeyhole size={16} /> Reveal booth & contact</button></div></article>; })}</div>}</>;
}

function OverlapsPageContent() {
  const searchParams = useSearchParams();
  const context = useResource(loadContext, []);
  const [eventId, setEventId] = useState("");
  if (context.loading) return <LoadingState label="Loading event context…" />;
  if (context.error || !context.data) return <ErrorState error={context.error} onRetry={context.reload} />;
  const requestedEventId = searchParams.get("event") ?? "";
  const requestedEventExists = context.data.events.some((event) => event.id === requestedEventId);
  const selectedEventId = eventId || (requestedEventExists ? requestedEventId : "") || context.data.events[0]?.id || "";
  return <><PageHeader eyebrow="Matched supply and demand" title="Opportunities" description="These matches exist only because both vendors are registered for the same event." action={context.data.events.length ? <label className="field event-select"><span>Event</span><select value={selectedEventId} onChange={(event) => setEventId(event.target.value)}>{context.data.events.map((event) => <option key={event.id} value={event.id}>{event.name}</option>)}</select></label> : undefined} />{context.data.events.length === 0 ? <EmptyState title="Join an event to start matching" description="Inventory and buy-list overlaps are always scoped to a shared convention." /> : selectedEventId ? <MatchResults key={selectedEventId} eventId={selectedEventId} profile={context.data.profile} /> : <div className="state-card"><Sparkles /> Preparing matches…</div>}</>;
}

export default function OverlapsPage() {
  return <Suspense fallback={<LoadingState label="Loading event context…" />}><OverlapsPageContent /></Suspense>;
}
