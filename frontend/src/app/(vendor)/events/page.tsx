"use client";

import Link from "next/link";
import { CalendarCheck, MapPin, Store, X } from "lucide-react";
import { useState, type FormEvent } from "react";

import { EmptyState, ErrorState, LoadingState, PageHeader, StatusPill } from "@/components/primitives";
import { useResource } from "@/hooks/use-resource";
import { api, messageFor } from "@/lib/api";
import { dateRange } from "@/lib/client-data";
import type { EventRegistration, EventSummary, PageResponse } from "@/lib/contracts";

async function loadEvents() {
  const [events, registrations] = await Promise.all([
    api<PageResponse<EventSummary>>("/events?pageSize=100"),
    api<PageResponse<EventRegistration>>("/events/registrations?pageSize=100"),
  ]);
  return { events: events.items, registrations: new Map(registrations.items.map((item) => [item.eventId, item])) };
}

function RegisterDialog({ event, onClose, onRegistered }: { event: EventSummary; onClose: () => void; onRegistered: () => void }) {
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function submit(formEvent: FormEvent<HTMLFormElement>) {
    formEvent.preventDefault(); setBusy(true); setError("");
    const form = new FormData(formEvent.currentTarget);
    try { await api<EventRegistration>(`/events/${event.id}/register`, { method: "POST", body: JSON.stringify({ booth: form.get("booth") }) }); onRegistered(); } catch (reason) { setError(messageFor(reason)); setBusy(false); }
  }
  return <div className="modal-backdrop" role="dialog" aria-modal="true"><form className="modal-card compact-modal" onSubmit={submit}><div className="panel-heading"><div><p className="eyebrow">Vendor registration</p><h2>Join {event.name}</h2></div><button className="close-button" type="button" onClick={onClose}><X size={18} /></button></div><p className="form-help">Your booth is visible only on authorized event roster pages. You can leave it blank until placement is assigned.</p><label className="field"><span>Booth number</span><input name="booth" maxLength={40} placeholder="B-17 (optional)" /></label>{error ? <p className="form-error">{error}</p> : null}<div className="form-actions"><button className="button secondary" type="button" onClick={onClose}>Cancel</button><button className="button primary" disabled={busy}>{busy ? "Registering…" : "Register for event"}</button></div></form></div>;
}

export default function EventsPage() {
  const resource = useResource(loadEvents, []);
  const [registering, setRegistering] = useState<EventSummary | null>(null);
  const [error, setError] = useState("");
  if (resource.loading) return <LoadingState label="Loading convention calendar…" />;
  if (resource.error || !resource.data) return <ErrorState error={resource.error} onRetry={resource.reload} />;
  const { events, registrations } = resource.data;

  async function unregister(event: EventSummary) {
    if (!window.confirm(`Unregister from ${event.name}? Event-scoped overlaps will no longer be available.`)) return;
    setError("");
    try { await api<void>(`/events/${event.id}/register`, { method: "DELETE" }); resource.reload(); } catch (reason) { setError(messageFor(reason)); }
  }

  return <><PageHeader eyebrow="Event context" title="Events" description="Join the conventions where your stock will be available. Matching is intentionally limited to shared events." />{error ? <p className="form-error">{error}</p> : null}{events.length === 0 ? <EmptyState title="No upcoming events" description="The operator event catalog is empty right now. Check back when the next convention is scheduled." /> : <section className="event-grid">{events.map((event) => { const registration = registrations.get(event.id); return <article className="panel event-card" key={event.id}><div className="event-card-top"><span className="event-date"><strong>{new Date(`${event.startDate}T12:00:00`).toLocaleDateString("en-US", { month: "short" })}</strong><b>{new Date(`${event.startDate}T12:00:00`).getDate()}</b></span>{registration ? <StatusPill tone="blue"><CalendarCheck size={13} /> Registered</StatusPill> : <StatusPill>Open</StatusPill>}</div><div><h2>{event.name}</h2><p>{event.description || "Trading card convention and vendor floor."}</p></div><ul className="event-meta"><li><MapPin size={15} /> {event.venue ? `${event.venue} · ` : ""}{event.city}, {event.state}</li><li><CalendarCheck size={15} /> {dateRange(event.startDate, event.endDate)}</li>{registration?.booth ? <li><Store size={15} /> Booth {registration.booth}</li> : null}</ul><div className="event-actions"><Link className="button secondary" href={`/events/${event.id}`}>View event</Link>{registration ? <button className="text-button danger" onClick={() => void unregister(event)}>Unregister</button> : <button className="button primary" onClick={() => setRegistering(event)}>Register</button>}</div></article>; })}</section>}{registering ? <RegisterDialog event={registering} onClose={() => setRegistering(null)} onRegistered={() => { setRegistering(null); resource.reload(); }} /> : null}</>;
}
