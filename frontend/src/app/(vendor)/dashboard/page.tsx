"use client";

import Link from "next/link";
import { ArrowRight, Check, Circle } from "lucide-react";

import { ErrorState, LoadingState, EmptyState } from "@/components/primitives";
import { useResource } from "@/hooks/use-resource";
import { api } from "@/lib/api";
import { dateRange, hydrateCards, money, relativeTime } from "@/lib/client-data";
import type {
  CardSummary,
  EventRegistration,
  EventSummary,
  InventoryItem,
  NotificationItem,
  Overlap,
  PageResponse,
  Profile,
  SavedOverlap,
  UnreadCount,
  WantedCard,
} from "@/lib/contracts";

type DashboardData = {
  profile: Profile;
  events: EventSummary[];
  registrations: EventRegistration[];
  inventory: PageResponse<InventoryItem>;
  buyList: PageResponse<WantedCard>;
  notifications: NotificationItem[];
  unread: number;
  activeEvent: EventSummary | null;
  overlaps: Overlap[];
  saved: SavedOverlap[];
  cards: Map<string, CardSummary>;
};

async function loadDashboard(): Promise<DashboardData> {
  const [profile, eventPage, registrationPage, inventory, buyList, notificationPage, unread] = await Promise.all([
    api<Profile>("/profile"),
    api<PageResponse<EventSummary>>("/events?pageSize=100"),
    api<PageResponse<EventRegistration>>("/events/registrations?pageSize=100"),
    api<PageResponse<InventoryItem>>("/inventory?pageSize=100"),
    api<PageResponse<WantedCard>>("/buylist?pageSize=100"),
    api<PageResponse<NotificationItem>>("/notifications?pageSize=5"),
    api<UnreadCount>("/notifications/unread-count"),
  ]);
  const registered = new Set(registrationPage.items.map((registration) => registration.eventId));
  const activeEvent = eventPage.items.find((event) => registered.has(event.id)) ?? null;
  const [overlapPage, savedPage] = activeEvent
    ? await Promise.all([
        api<PageResponse<Overlap>>(`/overlaps/event/${activeEvent.id}?pageSize=8`),
        api<PageResponse<SavedOverlap>>(`/overlaps/event/${activeEvent.id}/saved?pageSize=100`),
      ])
    : [{ items: [], nextPageOffset: 0, hasMore: false }, { items: [], nextPageOffset: 0, hasMore: false }];
  const cards = await hydrateCards([
    ...overlapPage.items.map((item) => item.cardId),
    ...notificationPage.items.map((item) => item.cardId),
  ]);
  return {
    profile,
    events: eventPage.items,
    registrations: registrationPage.items,
    inventory,
    buyList,
    notifications: notificationPage.items,
    unread: unread.unreadCount,
    activeEvent,
    overlaps: overlapPage.items,
    saved: savedPage.items,
    cards,
  };
}

export default function DashboardPage() {
  const resource = useResource(loadDashboard, []);
  if (resource.loading) return <LoadingState label="Building your event view…" />;
  if (resource.error || !resource.data) return <ErrorState error={resource.error} onRetry={resource.reload} />;
  const data = resource.data;
  const active = data.activeEvent;
  const inventoryCount = `${data.inventory.items.length}${data.inventory.hasMore ? "+" : ""}`;

  return (
    <>
      <section className="welcome-row">
        <div>
          <p className="eyebrow">{new Date().toLocaleDateString("en-US", { weekday: "long", month: "long", day: "numeric" })}</p>
          <h1>Good to see you, {data.profile.shopName}.</h1>
          <p className="lede">{active ? `${data.overlaps.length} current opportunities are tied to ${active.name}.` : "Join your next convention to switch matching on."}</p>
        </div>
        <Link className="event-picker" href="/events">
          <span><small>Active event</small>{active?.name ?? "Choose an event"}</span>
          <ArrowRight size={17} aria-hidden="true" />
        </Link>
      </section>

      {!active ? (
        <EmptyState title="Your event workspace is ready" description="Register for an upcoming convention, then import the inventory you plan to bring. VenDex will start looking for overlaps." action={<div className="state-actions"><Link className="button primary" href="/events">Browse events</Link><Link className="button secondary" href="/inventory">Add inventory</Link></div>} />
      ) : (
        <>
          <section className="stat-grid" aria-label="Event summary">
            <article className="stat-card"><span className="stat-kicker">Opportunities</span><strong>{data.overlaps.length}</strong><span className="stat-detail positive">Live for this event</span></article>
            <article className="stat-card"><span className="stat-kicker">Saved to plan</span><strong>{data.saved.length}</strong><span className="stat-detail">Ready for event day</span></article>
            <article className="stat-card"><span className="stat-kicker">Inventory</span><strong>{inventoryCount}</strong><span className="stat-detail">items in your workspace</span></article>
            <article className="stat-card emphasis"><span className="stat-kicker">Event dates</span><strong>{dateRange(active.startDate, active.endDate)}</strong><span className="stat-detail">{active.city}, {active.state}</span></article>
          </section>

          <div className="dashboard-grid">
            <section className="panel opportunities-panel">
              <div className="panel-heading"><div><p className="eyebrow">Live market</p><h2>Best opportunities</h2></div><Link href="/overlaps">View all</Link></div>
              {data.overlaps.length === 0 ? <EmptyState title="No overlaps yet" description="Your inventory and buy list are active. New matches will appear as other registered vendors update theirs." /> : (
                <div className="opportunity-list">
                  {data.overlaps.slice(0, 5).map((overlap) => {
                    const buying = overlap.buyerVendorId === data.profile.userId;
                    const card = data.cards.get(overlap.cardId);
                    return (
                      <article className="opportunity" key={overlap.id}>
                        <span className={`direction-dot ${buying ? "blue" : "warm"}`} aria-hidden="true" />
                        <div className="opportunity-copy"><span className="direction-label">{buying ? "You can buy" : "You can sell"}</span><strong>{card?.name ?? "Catalog card"}</strong><small>{overlap.counterparty?.shopName ?? "Registered vendor"}{overlap.counterparty?.booth ? ` · Booth ${overlap.counterparty.booth}` : ""}</small></div>
                        <div className="opportunity-action"><span>{buying ? `${money(overlap.askingPrice)} ask` : `${money(overlap.maxBuyPrice)} max`}</span><Link href="/overlaps">View</Link></div>
                      </article>
                    );
                  })}
                </div>
              )}
            </section>

            <aside className="right-rail">
              <section className="panel progress-panel">
                <p className="eyebrow">Show readiness</p><div className="progress-heading"><h2>Event setup</h2><strong>{Math.min(100, 25 + (data.buyList.items.length > 0 ? 25 : 0) + (data.inventory.items.length > 0 ? 25 : 0) + (data.saved.length > 0 ? 25 : 0))}%</strong></div>
                <div className="progress-track" aria-label="Event setup progress"><span style={{ width: `${Math.min(100, 25 + (data.buyList.items.length > 0 ? 25 : 0) + (data.inventory.items.length > 0 ? 25 : 0) + (data.saved.length > 0 ? 25 : 0))}%` }} /></div>
                <ul className="checklist">
                  <li className="done"><Check size={14} /> Registered for event</li>
                  <li className={data.buyList.items.length > 0 ? "done" : ""}>{data.buyList.items.length > 0 ? <Check size={14} /> : <Circle size={14} />} Buy list is active</li>
                  <li className={data.inventory.items.length > 0 ? "done" : ""}>{data.inventory.items.length > 0 ? <Check size={14} /> : <Circle size={14} />} Inventory is loaded</li>
                  <li className={data.saved.length > 0 ? "done" : ""}>{data.saved.length > 0 ? <Check size={14} /> : <Circle size={14} />} Save an opportunity</li>
                </ul>
              </section>
              <section className="panel notification-panel">
                <div className="panel-heading compact"><h2>Latest updates</h2><Link href="/notifications">{data.unread} unread</Link></div>
                {data.notifications.length === 0 ? <p className="panel-empty">You are caught up.</p> : data.notifications.slice(0, 3).map((item) => (
                  <div className={`notification-item${item.read ? " muted" : ""}`} key={item.id}><span className="notification-mark" /><p><strong>{item.trigger.replaceAll("_", " ")}</strong><br />{data.cards.get(item.cardId)?.name ?? "A card"} · {item.counterparty?.shopName ?? "Vendor update"}</p><time>{relativeTime(item.createdAtEpochSeconds)}</time></div>
                ))}
              </section>
            </aside>
          </div>
        </>
      )}
    </>
  );
}
