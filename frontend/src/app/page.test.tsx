import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import Home from "./page";

describe("dashboard preview", () => {
  it("renders the vendor's next-show summary", () => {
    render(<Home />);

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
      "Good afternoon, Taylor.",
    );
    expect(screen.getByRole("button", { name: /active event/i })).toHaveTextContent(
      "Collect-A-Con Dallas",
    );
  });
});
