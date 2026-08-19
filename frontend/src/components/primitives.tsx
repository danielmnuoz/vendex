import Image from "next/image";
import Link from "next/link";
import { AlertTriangle, Inbox, LoaderCircle } from "lucide-react";
import type { ReactNode } from "react";

import { messageFor } from "@/lib/api";
import type { CardSummary } from "@/lib/contracts";

export function PageHeader({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow: string;
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <header className="page-header">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        <p className="lede">{description}</p>
      </div>
      {action ? <div className="page-header-action">{action}</div> : null}
    </header>
  );
}

export function LoadingState({ label = "Loading your workspace…" }: { label?: string }) {
  return (
    <div className="state-card" role="status">
      <LoaderCircle className="spin" aria-hidden="true" />
      <strong>{label}</strong>
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <div className="state-card error-state" role="alert">
      <AlertTriangle aria-hidden="true" />
      <strong>That request did not land.</strong>
      <p>{messageFor(error)}</p>
      {onRetry ? <button className="button secondary" onClick={onRetry}>Try again</button> : null}
    </div>
  );
}

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <div className="state-card empty-state">
      <Inbox aria-hidden="true" />
      <strong>{title}</strong>
      <p>{description}</p>
      {action}
    </div>
  );
}

export function CardIdentity({ card, compact = false }: { card?: CardSummary; compact?: boolean }) {
  return (
    <div className={`card-identity${compact ? " compact" : ""}`}>
      <div className="card-thumb">
        {card?.imageUrl ? (
          <Image src={card.imageUrl} alt="" width={compact ? 34 : 44} height={compact ? 47 : 61} unoptimized />
        ) : (
          <span aria-hidden="true">?</span>
        )}
      </div>
      <span>
        <strong>{card?.name ?? "Catalog card"}</strong>
        <small>{card ? `${card.setName}${card.rarity ? ` · ${card.rarity}` : ""}` : "Metadata unavailable"}</small>
      </span>
    </div>
  );
}

export function StatusPill({ children, tone = "neutral" }: { children: ReactNode; tone?: "neutral" | "warm" | "blue" }) {
  return <span className={`status-pill ${tone}`}>{children}</span>;
}

export function TextLink({ href, children }: { href: string; children: ReactNode }) {
  return <Link className="text-link" href={href}>{children}</Link>;
}
