import { afterEach, describe, expect, it } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "../../auth/AuthContext";
import { testUser } from "../../test/testUser";
import PostsAdminPage from "./PostsAdminPage";

const renderPage = (role = "bureau") => {
  const user = testUser(role);
  render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider user={user}>
        <PostsAdminPage />
      </AuthProvider>
    </QueryClientProvider>
  );
};

describe("PostsAdminPage", () => {
  afterEach(() => localStorage.clear());

  it("shows each Post's status and author", async () => {
    renderPage();
    expect(await screen.findByRole("button", { name: /Retour sur la collecte 2025/ })).toHaveTextContent("Publié");
  });

  it("writes a Brouillon signed by the current person, then publishes and unpublishes it", async () => {
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: "+ Nouvelle actualité" }));
    await userEvent.type(screen.getByLabelText("Titre"), "Test sortie de printemps");
    await userEvent.type(screen.getByLabelText("Contenu"), "Rendez-vous samedi.");
    await userEvent.click(screen.getByRole("button", { name: "Créer le brouillon" }));

    const item = await screen.findByRole("button", { name: /Test sortie de printemps/ });
    expect(item).toHaveTextContent("Brouillon");
    expect(item).toHaveTextContent("Jean Dupont");

    await userEvent.click(item);
    await userEvent.click(screen.getByRole("button", { name: "Publier" }));
    await waitFor(() => expect(screen.getByRole("button", { name: /Test sortie de printemps/ })).toHaveTextContent("Publié"));

    // The Post stays open after publishing: its action flips
    await userEvent.click(await screen.findByRole("button", { name: "Repasser en brouillon" }));
    await waitFor(() => expect(screen.getByRole("button", { name: /Test sortie de printemps/ })).toHaveTextContent("Brouillon"));
  });

  it("lists only the Bureau's own Secteur's Posts (ADR 0004)", async () => {
    renderPage();
    expect(await screen.findByRole("button", { name: /Retour sur la collecte 2025/ })).toBeInTheDocument();
    expect(screen.queryByLabelText("Secteur")).not.toBeInTheDocument();
  });

  it("lets the Super admin write for a chosen Secteur", async () => {
    renderPage("super_admin");
    await userEvent.click(await screen.findByRole("button", { name: "+ Nouvelle actualité" }));
    await userEvent.selectOptions(await screen.findByLabelText("Secteur"), "sector-2");
    await userEvent.type(screen.getByLabelText("Titre"), "Balade à Thionville");
    await userEvent.type(screen.getByLabelText("Contenu"), "Rendez-vous dimanche.");
    await userEvent.click(screen.getByRole("button", { name: "Créer le brouillon" }));

    expect(await screen.findByRole("button", { name: /Balade à Thionville/ })).toHaveTextContent("Secteur Thionville");
  });
});
