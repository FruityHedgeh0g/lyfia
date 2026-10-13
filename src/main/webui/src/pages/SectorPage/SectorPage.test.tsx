import { afterEach, describe, expect, it } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "../../auth/AuthContext";
import { testUser } from "../../test/testUser";
import SectorPage from "./SectorPage";

const renderPage = (role = "bureau") => {
  const user = testUser(role);
  render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider user={user}>
        <SectorPage />
      </AuthProvider>
    </QueryClientProvider>
  );
};

describe("SectorPage (Mon secteur)", () => {
  afterEach(() => localStorage.clear());

  it("shows the Secteur and its Groupes with their Chef, without anyone's permanent Groupe", async () => {
    renderPage();

    expect(await screen.findByRole("heading", { name: "Secteur Algrange" })).toBeInTheDocument();
    // Only the Super admin renames a Secteur; the Bureau keeps its description
    expect(screen.queryByLabelText("Nom du secteur")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Description")).toBeEnabled();
    const groupes = await screen.findByRole("list", { name: "Groupes du secteur" });
    expect(within(groupes).getByText("Groupe Algrange Centre")).toBeInTheDocument();
    expect(within(groupes).getByText(/Marc Weber/)).toBeInTheDocument();
    expect(screen.queryByText(/Inscrits de mon secteur/)).not.toBeInTheDocument();
  });

  it("shows the Bureau its own Secteur only", async () => {
    renderPage();
    expect(await screen.findByRole("heading", { name: "Secteur Algrange" })).toBeInTheDocument();
    expect(screen.queryByLabelText("Secteur")).not.toBeInTheDocument();
  });
});
