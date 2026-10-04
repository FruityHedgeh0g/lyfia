import { describe, expect, it } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { AuthProvider } from "../../auth/AuthContext";
import { testUser } from "../../test/testUser";
import FeatureFlagsPage from "./FeatureFlagsPage";

const renderAs = (role: string) =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <AuthProvider user={testUser(role)}>
          <FeatureFlagsPage />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>
  );

describe("FeatureFlagsPage", () => {
  it("lets the Super admin switch one on", async () => {
    renderAs("super_admin");
    await userEvent.click(await screen.findByRole("button", { name: "Activer" }));
    await waitFor(() => expect(screen.queryByRole("button", { name: "Activer" })).not.toBeInTheDocument());
  });

  it("turns a Feature off with a reason and shows who did it (ADR 0009)", async () => {
    renderAs("super_admin");
    await userEvent.type(await screen.findByLabelText("Raison (facultatif) — Galerie photos"), "Spam");
    const gallery = screen.getByText("Galerie photos").closest("li") as HTMLElement;
    await userEvent.click(within(gallery).getByRole("button", { name: "Désactiver" }));

    expect(await within(gallery).findByText(/Désactivée par Jean Dupont .* — « Spam »/)).toBeInTheDocument();
    expect(within(gallery).getByText("0 tentative refusée depuis la coupure")).toBeInTheDocument();
  });

  it("turns Inscription aux événements off for one Secteur only (ADR 0009)", async () => {
    renderAs("super_admin");
    const perSecteur = await screen.findByRole("list", { name: "Inscription aux événements par secteur" });
    await userEvent.click(within(perSecteur).getByRole("button", { name: "Désactiver pour Secteur Algrange" }));

    expect(await within(perSecteur).findByRole("button", { name: "Activer pour Secteur Algrange" })).toBeInTheDocument();
    expect(within(perSecteur).getByText("Secteur Algrange : désactivée")).toBeInTheDocument();
    expect(screen.queryByRole("list", { name: "Galerie photos par secteur" })).not.toBeInTheDocument();
  });
});
