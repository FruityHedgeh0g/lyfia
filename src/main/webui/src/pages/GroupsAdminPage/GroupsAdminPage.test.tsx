import { afterEach, describe, expect, it } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "../../auth/AuthContext";
import { testUser } from "../../test/testUser";
import GroupsAdminPage from "./GroupsAdminPage";
import { seedGroup } from "../../test/fakeApi";

const renderPage = (role = "bureau") => {
  const user = testUser(role);
  render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider user={user}>
        <GroupsAdminPage />
      </AuthProvider>
    </QueryClientProvider>
  );
};

const chefOptions = () =>
  within(screen.getByLabelText("Chef de groupe")).getAllByRole("option").map((o) => o.textContent);

describe("GroupsAdminPage", () => {
  afterEach(() => {
    localStorage.clear();
  });

  it("lists each Groupe with its area and Chef", async () => {
    renderPage();
    const algrange = await screen.findByRole("button", { name: /Groupe Algrange Centre/ });
    expect(algrange).toHaveTextContent("Centre-ville d'Algrange");
    expect(algrange).toHaveTextContent("Chef : Marc Weber");
  });

  it("offers as Chef only people with Role at least chef_de_groupe", async () => {
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: /Groupe Algrange Centre/ }));
    // Only the Groupe's own Secteur's people lead it (ADR 0004), an Admin of that Secteur included
    expect(chefOptions()).toEqual(["Aucun chef", "Paul Muller", "Marc Weber", "Luc Schmitt"]);
  });

  it("moves a Chef who already leads another Groupe of the Secteur", async () => {
    seedGroup({ name: "Groupe Algrange Nord", sectorId: "sector-1", chef: { userId: "user-9", firstName: "Luc", lastName: "Schmitt" } });
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: /Groupe Algrange Centre/ }));
    await userEvent.selectOptions(screen.getByLabelText("Chef de groupe"), "user-9");
    await userEvent.click(screen.getByRole("button", { name: "Enregistrer" }));

    await waitFor(() =>
      expect(screen.getByRole("button", { name: /Groupe Algrange Centre/ })).toHaveTextContent("Chef : Luc Schmitt")
    );
    expect(screen.getByRole("button", { name: /Groupe Algrange Nord/ })).toHaveTextContent("Sans chef");
  });

  it("shows the Bureau only its own Secteur's Groupes, and locks the Secteur of a new one", async () => {
    renderPage();
    expect(await screen.findByRole("button", { name: /Groupe Algrange Centre/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Groupe Thionville/ })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "+ Nouveau groupe" }));
    expect(screen.getByLabelText("Secteur")).toBeDisabled();
    expect(screen.getByLabelText("Secteur")).toHaveValue("sector-1");
  });

  it("lets the Super admin see every Groupe and choose the Secteur of a new one", async () => {
    renderPage("super_admin");
    expect(await screen.findByRole("button", { name: /Groupe Thionville/ })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "+ Nouveau groupe" }));
    expect(screen.getByLabelText("Secteur")).toBeEnabled();
  });

  it("clears a Groupe's Chef", async () => {
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: /Groupe Algrange Centre/ }));
    await userEvent.selectOptions(screen.getByLabelText("Chef de groupe"), "");
    await userEvent.click(screen.getByRole("button", { name: "Enregistrer" }));

    await waitFor(() =>
      expect(screen.getByRole("button", { name: /Groupe Algrange Centre/ })).toHaveTextContent("Sans chef")
    );
  });

  it("creates a Groupe with its area", async () => {
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: "+ Nouveau groupe" }));
    await userEvent.type(screen.getByLabelText(/Nom du groupe/), "Groupe Nord");
    await userEvent.type(screen.getByLabelText("Zone couverte"), "Nord du secteur");
    await userEvent.click(screen.getByRole("button", { name: "Créer le groupe" }));

    expect(await screen.findByRole("button", { name: /Groupe Nord/ })).toHaveTextContent("Nord du secteur");
  });
});
