import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { ThemeProvider } from "../../../theme/ThemeContext";
import { AuthProvider } from "../../../auth/AuthContext";
import { testUser } from "../../../test/testUser";
import { setFakeFeature } from "../../../test/fakeApi";
import { setFeatureFlagActive } from "../../../features/featureFlags/featureFlagsApi";
import { ReadOnlyError } from "../../../lib/http";
import Header from "../Header/Header";
import PreviewBar from "./PreviewBar";

const renderAs = (role: string) =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <ThemeProvider>
          <AuthProvider user={testUser(role)}>
            <PreviewBar />
            <Header />
          </AuthProvider>
        </ThemeProvider>
      </MemoryRouter>
    </QueryClientProvider>
  );

const previewAs = (role: string) => userEvent.selectOptions(screen.getByLabelText("Aperçu en tant que"), role);

describe("PreviewBar (Aperçu)", () => {
  it("is the Super admin's only", () => {
    renderAs("admin");
    expect(screen.queryByRole("region", { name: "Aperçu" })).not.toBeInTheDocument();
  });

  it("shows the site as a Bénévole would see it, then gives the Super admin's view back (ADR 0009)", async () => {
    renderAs("super_admin");
    expect(await screen.findAllByRole("link", { name: "Administration" })).not.toHaveLength(0);

    await previewAs("benevole");
    expect(screen.getByText(/le site tel qu'un Bénévole le voit/)).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Administration" })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Quitter l'Aperçu" }));
    expect(screen.getAllByRole("link", { name: "Administration" })).not.toHaveLength(0);
  });

  it("gives a Secteur to the Roles that have one", async () => {
    renderAs("super_admin");
    await screen.findByLabelText("Aperçu en tant que");
    await previewAs("bureau");
    expect(await screen.findByText(/le site tel qu'un Bureau de Secteur Algrange le voit/)).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText("Secteur de l'Aperçu"), "sector-2");
    expect(screen.getByText(/le site tel qu'un Bureau de Secteur Thionville le voit/)).toBeInTheDocument();
  });

  it("shows what is turned off as the Role sees it, not marked as for the Super admin", async () => {
    setFakeFeature("galerie-photos", false);
    renderAs("super_admin");
    await previewAs("visiteur");
    await userEvent.click(screen.getAllByRole("button", { name: "Association" })[0]);
    expect(screen.queryByRole("link", { name: "Galerie photos" })).not.toBeInTheDocument();
  });

  it("lets nothing be done from an Aperçu", async () => {
    renderAs("super_admin");
    await previewAs("benevole");
    await expect(setFeatureFlagActive("galerie-photos", false)).rejects.toBeInstanceOf(ReadOnlyError);
  });
});
