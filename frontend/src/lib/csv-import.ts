import Papa from "papaparse";

import type { ImportCandidate, ImportIssue } from "@/lib/contracts";

/**
 * Applies explicit vendor choices to the original CSV while preserving all
 * columns and quoting. Backend row numbers include the header row, so data
 * rows are addressed with a two-row offset.
 */
export function applyCandidateCorrections(
  csv: string,
  issues: ImportIssue[],
  selections: ReadonlyMap<number, ImportCandidate>,
) {
  const parsed = Papa.parse<Record<string, string>>(csv, {
    header: true,
    skipEmptyLines: true,
  });
  for (const issue of issues) {
    const candidate = selections.get(issue.rowNumber);
    const row = parsed.data[issue.rowNumber - 2];
    if (!candidate || !row) continue;
    row.card_name = candidate.cardName;
    row.set_name = candidate.setName;
  }
  return Papa.unparse({ fields: parsed.meta.fields ?? [], data: parsed.data });
}
