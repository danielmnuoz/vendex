"use client";

import Link from "next/link";
import { ArrowLeft, CalendarDays, MapPin, Store, Users } from "lucide-react";
import { useParams } from "next/navigation";

import { EmptyState, ErrorState, LoadingState, PageHeader, StatusPill } from "@/components/primitives";
import { useResource } from "@/hooks/use-resource";
import { api } from "@/lib/api";
import { dateRange } from "@/lib/client-data";
import type { EventRegistration, EventSummary, PageResponse, VendorRoster } from "@/lib/contracts";

async function loadEvent(eventId: string) {
  const [event, registrationPage] = await Promise.all([
    api<EventSummary>(`/events/${eventId}`),
    api<PageResponse<EventRegistration>>("/events/registrations?pageSize=100"),
  ]);
  const registration = registrationPage.items.find((item) => item.eventId === eventId) ?? null;
  const roster = registration ? await api<VendorRoster>(`/events/${eventId}/vendors`) : { vendors: [] };
  return { event, registration, roster };
}

export default function EventDetailPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const resource = useResource(() => loadEvent(eventId), [eventId]);
  if (resource.loading) return <LoadingState label="Loading event workspace…" />;
  if (resource.error || !resource.data) return <ErrorState error={resource.error} onRetry={resource.reload} />;
  const { event, registration, roster } = resource.data;
  return <><Link className="back-link" href="/events"><ArrowLeft size={16} /> All events</Link><PageHeader eyebrow={registration ? "Registered event" : "Event details"} title={event.name} description={event.description || "Convention vendor coordination workspace."} action={registration ? <StatusPill tone="blue"><CalendarDays size={14} /> {dateRange(event.startDate, event.endDate)}</StatusPill> : undefined} /><section className="event-detail-grid"><article className="panel event-overview"><h2>Show details</h2><ul className="detail-list"><li><MapPin size={18} /><span><small>Location</small><strong>{event.venue ? `${event.venue} · ` : ""}{event.city}, {event.state}</strong></span></li><li><CalendarDays size={18} /><span><small>Dates</small><strong>{dateRange(event.startDate, event.endDate)}</strong></span></li><li><Store size={18} /><span><small>Your booth</small><strong>{registration?.booth || "Not assigned"}</strong></span></li></ul>{registration ? <div className="button-row"><Link className="button primary" href={`/overlaps?event=${event.id}`}>View opportunities</Link><Link className="button secondary" href={`/inventory?event=${event.id}&add=1`}>Add event inventory</Link></div> : <p className="form-help">Register from the event list to unlock roster and market data.</p>}</article><section className="panel roster-panel"><div className="panel-heading"><div><p className="eyebrow">Authorized roster</p><h2>Vendors at this event</h2></div><span className="roster-count"><Users size={16} /> {roster.vendors.length}</span></div>{!registration ? <EmptyState title="Roster is registration-only" description="Vendor names and booth assignments are visible only after you join this event." /> : roster.vendors.length === 0 ? <EmptyState title="No vendors listed" description="The roster has not populated yet." /> : <div className="roster-list">{roster.vendors.map((item) => <article key={item.id}><span className="avatar small">{item.vendor?.shopName?.slice(0, 2).toUpperCase() || "V"}</span><span><strong>{item.vendor?.shopName ?? "Registered vendor"}</strong><small>{item.vendor ? `${item.vendor.city}, ${item.vendor.state}` : "Vendor profile"}</small></span><b>{item.booth ? `Booth ${item.booth}` : "TBD"}</b></article>)}</div>}</section></section></>;
}
