import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import InventoryPage from "./page";
import { api } from "@/lib/api";

vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams() }));
vi.mock("@/lib/api", () => ({ api: vi.fn(), messageFor: String }));

const row = { rowNumber: 2, cardName: "Pikachu", setName: "Base Set", condition: "nm", quantity: 12, askingPrice: "8.00", confidence: 1 };
const item = { id: "stock-1", cardId: "card-1", condition: "nm", quantity: 12, askingPrice: "8.00", priority: "normal" };
let committed = false;

beforeEach(() => {
  committed = false;
  vi.mocked(api).mockReset();
  vi.mocked(api).mockImplementation(async (path, options) => {
    if (path === "/inventory/import") {
      const dryRun = JSON.parse(String(options?.body)).dryRun;
      if (!dryRun) committed = true;
      return { resolvedRows: [row], issues: [], importedItems: dryRun ? [] : [item] };
    }
    if (path === "/cards/batch") return { cards: [] };
    return { items: path.startsWith("/inventory?") && committed ? [item] : [], hasMore: false };
  });
});

describe("CSV completion", () => {
  it.each(["Return to inventory", "Close CSV import"])("keeps the result visible until %s, then refreshes stock", async (closeAction) => {
    render(<InventoryPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Import CSV" }));
    const dialog = screen.getByRole("dialog");
    const input = dialog.querySelector('input[type="file"]')!;
    fireEvent.change(input, { target: { files: [{ name: "inventory.csv", text: async () => "card_name,set_name,condition,quantity,price,priority\nPikachu,Base Set,nm,12,8,normal" }] } });
    await waitFor(() => expect(screen.getByRole("button", { name: "Analyze rows" })).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "Analyze rows" }));
    fireEvent.click(await screen.findByRole("button", { name: "Build preview" }));
    fireEvent.click(await screen.findByRole("button", { name: "Import 1 rows" }));
    expect(await screen.findByRole("heading", { name: "Import complete" })).toBeVisible();
    expect(screen.getByText("1 inventory items imported")).toBeVisible();
    expect(screen.queryByRole("heading", { name: "Choose a file" })).not.toBeInTheDocument();
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: closeAction }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(await screen.findByRole("cell", { name: "$8.00" })).toBeVisible();
  });
});
