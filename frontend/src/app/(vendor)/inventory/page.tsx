"use client";

import { FileUp, Pencil, Plus, Trash2, X } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { Suspense, useMemo, useState, type FormEvent } from "react";

import { CardSearch } from "@/components/card-search";
import { CardIdentity, EmptyState, ErrorState, LoadingState, PageHeader, StatusPill } from "@/components/primitives";
import { useResource } from "@/hooks/use-resource";
import { api, messageFor } from "@/lib/api";
import { conditionLabel, hydrateCards, money } from "@/lib/client-data";
import { applyCandidateCorrections } from "@/lib/csv-import";
import type { CardSummary, EventRegistration, EventSummary, ImportCandidate, ImportResponse, InventoryItem, PageResponse } from "@/lib/contracts";

type InventoryData = {
  page: PageResponse<InventoryItem>;
  cards: Map<string, CardSummary>;
  events: EventSummary[];
};

async function loadInventory(): Promise<InventoryData> {
  const [page, eventPage, registrations] = await Promise.all([
    api<PageResponse<InventoryItem>>("/inventory?pageSize=100"),
    api<PageResponse<EventSummary>>("/events?pageSize=100"),
    api<PageResponse<EventRegistration>>("/events/registrations?pageSize=100"),
  ]);
  const registered = new Set(registrations.items.map((item) => item.eventId));
  return {
    page,
    cards: await hydrateCards(page.items.map((item) => item.cardId)),
    events: eventPage.items.filter((event) => registered.has(event.id)),
  };
}

function InventoryForm({
  item,
  initialCard,
  events,
  defaultEventId,
  onSaved,
  onCancel,
}: {
  item: InventoryItem | null;
  initialCard: CardSummary | null;
  events: EventSummary[];
  defaultEventId?: string;
  onSaved: () => void;
  onCancel: () => void;
}) {
  const [selected, setSelected] = useState(initialCard);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!item && !selected) { setError("Choose a catalog card first."); return; }
    setBusy(true);
    setError("");
    const form = new FormData(event.currentTarget);
    const common = {
      eventId: form.get("eventId"),
      condition: form.get("condition"),
      gradingCompany: form.get("gradingCompany"),
      grade: form.get("grade"),
      quantity: Number(form.get("quantity")),
      askingPrice: form.get("askingPrice"),
      priority: form.get("priority"),
    };
    try {
      await api<InventoryItem>(item ? `/inventory/${item.id}` : "/inventory", {
        method: item ? "PUT" : "POST",
        body: JSON.stringify(item ? common : { cardId: selected?.id, ...common }),
      });
      onSaved();
    } catch (reason) {
      setError(messageFor(reason));
      setBusy(false);
    }
  }

  return (
    <form className="panel editor-panel" onSubmit={submit}>
      <div className="panel-heading"><div><p className="eyebrow">{item ? "Update listing" : "New inventory"}</p><h2>{item ? "Edit inventory item" : "Add a catalog card"}</h2></div><button className="close-button" type="button" onClick={onCancel} aria-label="Close editor"><X size={18} /></button></div>
      {item ? <CardIdentity card={selected ?? undefined} /> : <CardSearch selected={selected} onSelect={setSelected} onClear={() => setSelected(null)} />}
      <div className="field-grid four">
        <label className="field"><span>Condition</span><select name="condition" defaultValue={item?.condition ?? "nm"}><option value="nm">NM</option><option value="lp">LP</option><option value="mp">MP</option><option value="hp">HP</option><option value="dmg">DMG</option></select></label>
        <label className="field"><span>Quantity</span><input name="quantity" type="number" min="1" required defaultValue={item?.quantity ?? 1} /></label>
        <label className="field"><span>Asking price</span><input name="askingPrice" inputMode="decimal" required defaultValue={item?.askingPrice ?? "0.00"} /></label>
        <label className="field"><span>Priority</span><select name="priority" defaultValue={item?.priority ?? "normal"}><option value="normal">Normal</option><option value="liquidate">Liquidate</option></select></label>
      </div>
      <div className="field-grid three">
        <label className="field"><span>Event scope</span><select name="eventId" defaultValue={item?.eventId || defaultEventId || ""}><option value="">Available generally</option>{events.map((event) => <option value={event.id} key={event.id}>{event.name}</option>)}</select></label>
        <label className="field"><span>Grading company</span><input name="gradingCompany" maxLength={80} defaultValue={item?.gradingCompany ?? ""} placeholder="Optional" /></label>
        <label className="field"><span>Grade</span><input name="grade" maxLength={20} defaultValue={item?.grade ?? ""} placeholder="Optional" /></label>
      </div>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <div className="form-actions"><button className="button secondary" type="button" onClick={onCancel}>Cancel</button><button className="button primary" disabled={busy} type="submit">{busy ? "Saving…" : item ? "Save changes" : "Add inventory"}</button></div>
    </form>
  );
}

function CsvImport({ events, onDone, onClose }: { events: EventSummary[]; onDone: () => void; onClose: () => void }) {
  const [step, setStep] = useState(1);
  const [csv, setCsv] = useState("");
  const [fileName, setFileName] = useState("");
  const [eventId, setEventId] = useState("");
  const [analysis, setAnalysis] = useState<ImportResponse | null>(null);
  const [preview, setPreview] = useState<ImportResponse | null>(null);
  const [selections, setSelections] = useState(new Map<number, ImportCandidate>());
  const [result, setResult] = useState<ImportResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const correctedCsv = useMemo(() => analysis ? applyCandidateCorrections(csv, analysis.issues, selections) : csv, [analysis, csv, selections]);

  async function dryRun(content: string) {
    return api<ImportResponse>("/inventory/import", { method: "POST", body: JSON.stringify({ eventId, csvContent: content, dryRun: true }) });
  }

  async function analyze() {
    if (!csv) return;
    setBusy(true); setError("");
    try { setAnalysis(await dryRun(csv)); setStep(2); } catch (reason) { setError(messageFor(reason)); } finally { setBusy(false); }
  }

  async function buildPreview() {
    setBusy(true); setError("");
    try { setPreview(await dryRun(correctedCsv)); setStep(3); } catch (reason) { setError(messageFor(reason)); } finally { setBusy(false); }
  }

  async function commit() {
    setBusy(true); setError("");
    try {
      const response = await api<ImportResponse>("/inventory/import", { method: "POST", body: JSON.stringify({ eventId, csvContent: correctedCsv, dryRun: false }) });
      setResult(response); setStep(4); onDone();
    } catch (reason) { setError(messageFor(reason)); } finally { setBusy(false); }
  }

  function selectCandidate(row: number, candidate: ImportCandidate) {
    setSelections((current) => { const next = new Map(current); next.set(row, candidate); return next; });
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="csv-title">
      <section className="modal-card csv-modal">
        <div className="panel-heading"><div><p className="eyebrow">CSV import</p><h2 id="csv-title">{["Choose a file", "Resolve matches", "Review import", "Import complete"][step - 1]}</h2></div><button className="close-button" type="button" onClick={onClose} aria-label="Close CSV import"><X size={18} /></button></div>
        <ol className="stepper" aria-label="Import progress">{["Upload", "Resolve", "Preview", "Commit"].map((label, index) => <li className={step >= index + 1 ? "active" : ""} key={label}><span>{index + 1}</span>{label}</li>)}</ol>
        {step === 1 ? <div className="import-step"><label className="file-drop"><FileUp size={28} /><strong>{fileName || "Choose a CSV inventory export"}</strong><span>Required: card_name, set_name, condition, quantity, price, priority</span><input type="file" accept=".csv,text/csv" onChange={async (event) => { const file = event.target.files?.[0]; if (!file) return; setFileName(file.name); setCsv(await file.text()); }} /></label><label className="field"><span>Scope imported stock to an event</span><select value={eventId} onChange={(event) => setEventId(event.target.value)}><option value="">Available generally</option>{events.map((event) => <option key={event.id} value={event.id}>{event.name}</option>)}</select></label><div className="form-actions"><button className="button primary" type="button" disabled={!csv || busy} onClick={analyze}>{busy ? "Analyzing…" : "Analyze rows"}</button></div></div> : null}
        {step === 2 && analysis ? <div className="import-step"><div className="import-summary"><strong>{analysis.resolvedRows.length} auto-matched</strong><span>{analysis.issues.length} rows need attention</span></div>{analysis.issues.length === 0 ? <EmptyState title="Every row matched safely" description="Continue to preview the exact records before committing." /> : <div className="issue-list">{analysis.issues.map((issue) => <article className="issue-card" key={issue.rowNumber}><header><span>Row {issue.rowNumber}</span><strong>{issue.cardName} · {issue.setName}</strong><small>{issue.reason}</small></header>{issue.candidates.length > 0 ? <div className="candidate-list">{issue.candidates.map((candidate) => <button className={selections.get(issue.rowNumber)?.cardId === candidate.cardId ? "selected" : ""} key={candidate.cardId} type="button" onClick={() => selectCandidate(issue.rowNumber, candidate)}><span><strong>{candidate.cardName}</strong><small>{candidate.setName}</small></span><b>{Math.round(candidate.confidence * 100)}%</b></button>)}</div> : <p className="form-help">No catalog candidates were found. This row will be skipped unless you correct the source CSV.</p>}</article>)}</div>}<div className="form-actions"><button className="button secondary" type="button" onClick={() => setStep(1)}>Back</button><button className="button primary" type="button" disabled={busy} onClick={buildPreview}>{busy ? "Rechecking…" : "Build preview"}</button></div></div> : null}
        {step === 3 && preview ? <div className="import-step"><div className="import-summary"><strong>{preview.resolvedRows.length} rows ready</strong><span>{preview.issues.length} rows will be skipped</span></div><div className="preview-table"><table><thead><tr><th>Row</th><th>Catalog match</th><th>Condition</th><th>Qty</th><th>Ask</th><th>Confidence</th></tr></thead><tbody>{preview.resolvedRows.map((row) => <tr key={row.rowNumber}><td>{row.rowNumber}</td><td><strong>{row.cardName}</strong><small>{row.setName}</small></td><td>{conditionLabel(row.condition)}</td><td>{row.quantity}</td><td>{money(row.askingPrice)}</td><td>{Math.round(row.confidence * 100)}%</td></tr>)}</tbody></table></div>{preview.issues.length > 0 ? <p className="form-help">Skipped rows remain in the report; committed rows are still written atomically by Inventory Service.</p> : null}<div className="form-actions"><button className="button secondary" type="button" onClick={() => setStep(2)}>Back</button><button className="button primary" disabled={busy || preview.resolvedRows.length === 0} type="button" onClick={commit}>{busy ? "Importing…" : `Import ${preview.resolvedRows.length} rows`}</button></div></div> : null}
        {step === 4 && result ? <div className="import-step"><EmptyState title={`${result.importedItems.length} inventory items imported`} description={result.issues.length ? `${result.issues.length} rows were skipped and remain available in the preview report.` : "Every reviewed row was committed successfully."} action={<button className="button primary" type="button" onClick={onClose}>Return to inventory</button>} /></div> : null}
        {error ? <p className="form-error" role="alert">{error}</p> : null}
      </section>
    </div>
  );
}

function InventoryPageContent() {
  const searchParams = useSearchParams();
  const resource = useResource(loadInventory, []);
  const requestedEventId = searchParams.get("event") ?? "";
  const [editing, setEditing] = useState<InventoryItem | null | undefined>(() => searchParams.get("add") === "1" ? null : undefined);
  const [importing, setImporting] = useState(false);
  const [mutationError, setMutationError] = useState("");
  if (resource.loading) return <LoadingState label="Loading inventory…" />;
  if (resource.error || !resource.data) return <ErrorState error={resource.error} onRetry={resource.reload} />;
  const { page, cards, events } = resource.data;

  async function remove(item: InventoryItem) {
    if (!window.confirm(`Remove ${cards.get(item.cardId)?.name ?? "this item"} from inventory?`)) return;
    setMutationError("");
    try { await api<void>(`/inventory/${item.id}`, { method: "DELETE" }); resource.reload(); } catch (reason) { setMutationError(messageFor(reason)); }
  }

  return (
    <>
      <PageHeader eyebrow="Supply" title="Inventory" description="Keep your available stock current and scope cards to the events where buyers can actually find you." action={<div className="button-row"><button className="button secondary icon-label" onClick={() => setImporting(true)}><FileUp size={17} /> Import CSV</button><button className="button primary icon-label" onClick={() => setEditing(null)}><Plus size={17} /> Add card</button></div>} />
      {editing !== undefined ? <InventoryForm item={editing} initialCard={editing ? cards.get(editing.cardId) ?? null : null} events={events} defaultEventId={events.some((event) => event.id === requestedEventId) ? requestedEventId : ""} onCancel={() => setEditing(undefined)} onSaved={() => { setEditing(undefined); resource.reload(); }} /> : null}
      {mutationError ? <p className="form-error" role="alert">{mutationError}</p> : null}
      {page.items.length === 0 ? <EmptyState title="No inventory yet" description="Add a card manually or import the CSV you already use to manage stock." action={<button className="button primary" onClick={() => setEditing(null)}>Add your first card</button>} /> : <section className="panel data-panel"><div className="table-toolbar"><span><strong>{page.items.length}{page.hasMore ? "+" : ""}</strong> items loaded</span><small>Supply is only discoverable through specific card searches.</small></div><div className="responsive-table"><table><thead><tr><th>Card</th><th>Condition</th><th>Quantity</th><th>Ask</th><th>Scope</th><th>Priority</th><th><span className="sr-only">Actions</span></th></tr></thead><tbody>{page.items.map((item) => <tr key={item.id}><td><CardIdentity card={cards.get(item.cardId)} compact /></td><td><StatusPill>{conditionLabel(item.condition)}{item.grade ? ` · ${item.gradingCompany} ${item.grade}` : ""}</StatusPill></td><td>{item.quantity}</td><td><strong>{money(item.askingPrice)}</strong></td><td>{events.find((event) => event.id === item.eventId)?.name ?? "General"}</td><td><StatusPill tone={item.priority === "liquidate" ? "warm" : "blue"}>{item.priority}</StatusPill></td><td><div className="row-actions"><button onClick={() => setEditing(item)} aria-label="Edit inventory item"><Pencil size={16} /></button><button onClick={() => void remove(item)} aria-label="Remove inventory item"><Trash2 size={16} /></button></div></td></tr>)}</tbody></table></div></section>}
      {importing ? <CsvImport events={events} onDone={resource.reload} onClose={() => setImporting(false)} /> : null}
    </>
  );
}

export default function InventoryPage() {
  return <Suspense fallback={<LoadingState label="Loading inventory…" />}><InventoryPageContent /></Suspense>;
}
