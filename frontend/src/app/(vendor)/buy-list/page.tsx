"use client";

import { Pencil, Plus, Trash2, X } from "lucide-react";
import { useState, type FormEvent } from "react";

import { CardSearch } from "@/components/card-search";
import { CardIdentity, EmptyState, ErrorState, LoadingState, PageHeader, StatusPill } from "@/components/primitives";
import { useResource } from "@/hooks/use-resource";
import { api, messageFor } from "@/lib/api";
import { conditionLabel, hydrateCards, money } from "@/lib/client-data";
import type { CardSummary, PageResponse, WantedCard } from "@/lib/contracts";

type BuyListData = { page: PageResponse<WantedCard>; cards: Map<string, CardSummary> };

async function loadBuyList(): Promise<BuyListData> {
  const page = await api<PageResponse<WantedCard>>("/buylist?pageSize=100");
  return { page, cards: await hydrateCards(page.items.map((item) => item.cardId)) };
}

function WantedCardForm({ item, card, onSaved, onCancel }: { item: WantedCard | null; card: CardSummary | null; onSaved: () => void; onCancel: () => void }) {
  const [selected, setSelected] = useState(card);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!item && !selected) { setError("Choose a catalog card first."); return; }
    const form = new FormData(event.currentTarget);
    const fields = { minimumCondition: form.get("minimumCondition"), maxBuyPrice: form.get("maxBuyPrice"), quantityWanted: Number(form.get("quantityWanted")) };
    setBusy(true); setError("");
    try {
      await api<WantedCard>(item ? `/buylist/${item.id}` : "/buylist", { method: item ? "PUT" : "POST", body: JSON.stringify(item ? fields : { cardId: selected?.id, ...fields }) });
      onSaved();
    } catch (reason) { setError(messageFor(reason)); setBusy(false); }
  }

  return (
    <form className="panel editor-panel" onSubmit={submit}>
      <div className="panel-heading"><div><p className="eyebrow">Demand</p><h2>{item ? "Update wanted card" : "Add to your buy list"}</h2></div><button className="close-button" type="button" onClick={onCancel}><X size={18} /></button></div>
      {item ? <CardIdentity card={selected ?? undefined} /> : <CardSearch selected={selected} onSelect={setSelected} onClear={() => setSelected(null)} />}
      <div className="field-grid three">
        <label className="field"><span>Minimum condition</span><select name="minimumCondition" defaultValue={item?.minimumCondition ?? "lp"}><option value="nm">NM</option><option value="lp">LP or better</option><option value="mp">MP or better</option><option value="hp">HP or better</option><option value="dmg">Any condition</option></select></label>
        <label className="field"><span>Maximum buy price</span><input name="maxBuyPrice" inputMode="decimal" required defaultValue={item?.maxBuyPrice ?? "0.00"} /></label>
        <label className="field"><span>Quantity wanted</span><input name="quantityWanted" type="number" min="1" required defaultValue={item?.quantityWanted ?? 1} /></label>
      </div>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <div className="form-actions"><button className="button secondary" type="button" onClick={onCancel}>Cancel</button><button className="button primary" disabled={busy}>{busy ? "Saving…" : item ? "Save changes" : "Add wanted card"}</button></div>
    </form>
  );
}

export default function BuyListPage() {
  const resource = useResource(loadBuyList, []);
  const [editing, setEditing] = useState<WantedCard | null | undefined>(undefined);
  const [error, setError] = useState("");
  if (resource.loading) return <LoadingState label="Loading your buy list…" />;
  if (resource.error || !resource.data) return <ErrorState error={resource.error} onRetry={resource.reload} />;
  const { page, cards } = resource.data;

  async function remove(item: WantedCard) {
    if (!window.confirm(`Remove ${cards.get(item.cardId)?.name ?? "this card"} from your buy list?`)) return;
    try { await api<void>(`/buylist/${item.id}`, { method: "DELETE" }); resource.reload(); } catch (reason) { setError(messageFor(reason)); }
  }

  return (
    <>
      <PageHeader eyebrow="Demand" title="Buy list" description="Publish the cards you are actively buying. Registered vendors can browse this demand before the event." action={<button className="button primary icon-label" onClick={() => setEditing(null)}><Plus size={17} /> Add wanted card</button>} />
      {editing !== undefined ? <WantedCardForm item={editing} card={editing ? cards.get(editing.cardId) ?? null : null} onCancel={() => setEditing(undefined)} onSaved={() => { setEditing(undefined); resource.reload(); }} /> : null}
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      {page.items.length === 0 ? <EmptyState title="Your buy list is open" description="Add the first card you would make a deal for at your next show." action={<button className="button primary" onClick={() => setEditing(null)}>Add a wanted card</button>} /> : <section className="panel data-panel"><div className="table-toolbar"><span><strong>{page.items.length}{page.hasMore ? "+" : ""}</strong> wanted cards</span><small>Demand can be browsed by other registered vendors.</small></div><div className="responsive-table"><table><thead><tr><th>Card</th><th>Minimum condition</th><th>Quantity</th><th>Max price</th><th><span className="sr-only">Actions</span></th></tr></thead><tbody>{page.items.map((item) => <tr key={item.id}><td><CardIdentity card={cards.get(item.cardId)} compact /></td><td><StatusPill tone="blue">{conditionLabel(item.minimumCondition)}+</StatusPill></td><td>{item.quantityWanted}</td><td><strong>{money(item.maxBuyPrice)}</strong></td><td><div className="row-actions"><button onClick={() => setEditing(item)} aria-label="Edit wanted card"><Pencil size={16} /></button><button onClick={() => void remove(item)} aria-label="Remove wanted card"><Trash2 size={16} /></button></div></td></tr>)}</tbody></table></div></section>}
    </>
  );
}
