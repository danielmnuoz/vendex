import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import Home from "./page";

describe("marketing landing page", () => {
  it("explains the event-scoped vendor workflow", () => {
    render(<Home />);

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
      "Find the right booth before the doors open.",
    );
    expect(screen.getByRole("link", { name: /create your vendor account/i })).toHaveAttribute("href", "/signup");
    expect(screen.getByRole("heading", { name: "Join the same event" })).toBeInTheDocument();
  });
});
