import Papa from "papaparse";
import { describe, expect, it } from "vitest";

import { applyCandidateCorrections } from "@/lib/csv-import";
import type { ImportCandidate, ImportIssue } from "@/lib/contracts";

const candidate: ImportCandidate = {
  cardId: "card-42",
  cardName: "Jace, the Mind Sculptor",
  setName: "Worldwake",
  confidence: 0.97,
};

const issue: ImportIssue = {
  rowNumber: 3,
  cardName: "Jace Mind Sculptor",
  setName: "WWK",
  reason: "AMBIGUOUS_MATCH",
  candidates: [candidate],
};

describe("applyCandidateCorrections", () => {
  it("corrects the selected row and preserves quoted values and extra columns", () => {
    const source = [
      "card_name,set_name,condition,quantity,price,priority,notes",
      'Lightning Bolt,Magic 2010,nm,4,1.25,normal,"binder, left side"',
      "Jace Mind Sculptor,WWK,lp,1,54.00,normal,display case",
    ].join("\n");

    const corrected = applyCandidateCorrections(source, [issue], new Map([[3, candidate]]));
    const rows = Papa.parse<Record<string, string>>(corrected, { header: true }).data;

    expect(rows[0]).toMatchObject({
      card_name: "Lightning Bolt",
      notes: "binder, left side",
    });
    expect(rows[1]).toMatchObject({
      card_name: candidate.cardName,
      set_name: candidate.setName,
      condition: "lp",
      notes: "display case",
    });
  });

  it("leaves unresolved rows unchanged", () => {
    const source = "card_name,set_name,condition\nJace Mind Sculptor,WWK,lp";

    expect(applyCandidateCorrections(source, [issue], new Map())).toContain(
      "Jace Mind Sculptor,WWK,lp",
    );
  });
});
