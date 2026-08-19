"use client";

import { Search } from "lucide-react";
import { useState } from "react";

import { api, messageFor } from "@/lib/api";
import type { CardSearchResponse, CardSummary } from "@/lib/contracts";
import { CardIdentity } from "@/components/primitives";

export function CardSearch({
  selected,
  onSelect,
  onClear,
}: {
  selected: CardSummary | null;
  onSelect: (card: CardSummary) => void;
  onClear: () => void;
}) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<CardSummary[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function search() {
    if (!query.trim()) return;
    setBusy(true);
    setError("");
    try {
      const response = await api<CardSearchResponse>(`/cards/search?query=${encodeURIComponent(query)}&pageSize=8`);
      setResults(response.cards);
    } catch (reason) {
      setError(messageFor(reason));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="card-search">
      {selected ? (
        <div className="selected-card">
          <CardIdentity card={selected} />
          <button className="text-button" type="button" onClick={() => { onClear(); setResults([]); setQuery(""); }}>
            Search again
          </button>
        </div>
      ) : null}
      <div className="search-row">
        <label className="field grow">
          <span>Card name</span>
          <input value={query} onChange={(event) => setQuery(event.target.value)} onKeyDown={(event) => {
            if (event.key === "Enter") { event.preventDefault(); void search(); }
          }} placeholder="Search Pikachu, Charizard…" />
        </label>
        <button className="button secondary icon-label" type="button" onClick={search} disabled={busy || !query.trim()}>
          <Search size={17} aria-hidden="true" /> {busy ? "Searching" : "Search"}
        </button>
      </div>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      {results.length > 0 ? (
        <div className="search-results" role="listbox" aria-label="Card results">
          {results.map((card) => (
            <button key={card.id} type="button" role="option" aria-selected={selected?.id === card.id} onClick={() => onSelect(card)}>
              <CardIdentity card={card} compact />
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}
