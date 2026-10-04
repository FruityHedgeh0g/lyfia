import { describe, expect, it } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { AuthProvider } from "../../auth/AuthContext";
import { testUser } from "../../test/testUser";
import { seedJournal } from "../../test/fakeApi";
import JournalPage from "./JournalPage";

const renderPage = () =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <AuthProvider user={testUser("super_admin")}>
          <JournalPage />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>
  );

describe("JournalPage", () => {
  it("says when nothing was ever switched", async () => {
    renderPage();
    expect(await screen.findByText("Aucune fonctionnalité n'a encore été changée.")).toBeInTheDocument();
  });

  it("loads the Journal one page at a time (ADR 0009)", async () => {
    seedJournal(25);
    renderPage();
    const journal = await screen.findByRole("region", { name: "Journal" });
    expect(within(journal).getAllByRole("listitem")).toHaveLength(20);

    await userEvent.click(screen.getByRole("button", { name: "Voir plus" }));
    expect(await within(journal).findAllByRole("listitem")).toHaveLength(25);
    expect(screen.queryByRole("button", { name: "Voir plus" })).not.toBeInTheDocument();
  });
});
