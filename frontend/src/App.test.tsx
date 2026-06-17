import { describe, expect, it, vi, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import App, { greetingUrl } from "./App";

describe("greetingUrl", () => {
  it("omits the query string when name is empty", () => {
    expect(greetingUrl("")).toBe("/api/greeting");
  });

  it("encodes the name parameter", () => {
    expect(greetingUrl("Ada Lovelace")).toBe("/api/greeting?name=Ada%20Lovelace");
  });
});

describe("<App />", () => {
  afterEach(() => vi.restoreAllMocks());

  it("renders and shows the greeting returned by the API", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        json: async () => ({ message: "Hello, Ada!" }),
      }),
    );

    render(<App />);
    fireEvent.change(screen.getByLabelText("name"), { target: { value: "Ada" } });
    fireEvent.click(screen.getByText("Greet"));

    await waitFor(() =>
      expect(screen.getByRole("status")).toHaveTextContent("Hello, Ada!"),
    );
  });
});
