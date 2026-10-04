import { afterEach, describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { AuthProvider } from "../../auth/AuthContext";
import { testUser } from "../../test/testUser";
import { SecteurChoiceProvider } from "./secteurChoice";
import SecteurSelect from "../../components/molecules/SecteurSelect/SecteurSelect";
import PostList from "../../components/organisms/PostList/PostList";

const renderNews = (role = "visiteur") =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <AuthProvider user={testUser(role)}>
          <SecteurChoiceProvider>
            <SecteurSelect />
            <PostList />
          </SecteurChoiceProvider>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>
  );

describe("public Secteur choice", () => {
  afterEach(() => localStorage.clear());

  it("shows every Secteur's news by default", async () => {
    renderNews();
    expect(await screen.findByText("Retour sur la collecte 2025")).toBeInTheDocument();
    expect(screen.getByLabelText("Secteur")).toHaveValue("");
  });

  it("filters the news by Secteur and remembers the choice on the device", async () => {
    const { unmount } = renderNews();
    await screen.findByText("Retour sur la collecte 2025");
    await userEvent.selectOptions(screen.getByLabelText("Secteur"), "sector-2");
    expect(await screen.findByText("Aucune actualité publiée pour le moment.")).toBeInTheDocument();
    unmount();

    renderNews();
    expect(await screen.findByText("Aucune actualité publiée pour le moment.")).toBeInTheDocument();
    expect(screen.getByLabelText("Secteur")).toHaveValue("sector-2");
  });

  it("never offers a Secteur fermé, even to the Super admin", async () => {
    testUser("super_admin");
    await fetch("/api/sectors/sector-2/close", { method: "POST" });
    renderNews("super_admin");
    await screen.findByText("Retour sur la collecte 2025");
    const offered = Array.from((screen.getByLabelText("Secteur") as HTMLSelectElement).options).map((o) => o.textContent);
    expect(offered).toEqual(["Tous les secteurs", "Secteur Algrange"]);
  });
});
