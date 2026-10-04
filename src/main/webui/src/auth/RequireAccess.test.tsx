import { afterEach, describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./AuthContext";
import RequireAccess from "./RequireAccess";
import { AccessId } from "./access";
import { testUser } from "../test/testUser";
import { setFakeFeature } from "../test/fakeApi";

const renderProtected = (id: AccessId, role = "benevole") =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={["/protected"]}>
        <AuthProvider user={testUser(role)}>
          <Routes>
            <Route path="/" element={<p>Accueil</p>} />
            <Route
              path="/protected"
              element={
                <RequireAccess id={id}>
                  <p>Contenu protégé</p>
                </RequireAccess>
              }
            />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>
  );

describe("RequireAccess", () => {
  afterEach(() => {
    localStorage.clear();
  });

  it("redirects to the home page when the role is insufficient", () => {
    renderProtected("account", "visiteur");
    expect(screen.getByText("Accueil")).toBeInTheDocument();
    expect(screen.queryByText("Contenu protégé")).not.toBeInTheDocument();
  });

  it("renders the children when the role is sufficient", () => {
    renderProtected("account", "benevole");
    expect(screen.getByText("Contenu protégé")).toBeInTheDocument();
  });

  it("shows the unavailable page once the entry's feature is known to be inactive", async () => {
    setFakeFeature("galerie-photos", false);
    renderProtected("gallery");
    expect(await screen.findByText("Fonctionnalité temporairement indisponible")).toBeInTheDocument();
    expect(screen.queryByText("Contenu protégé")).not.toBeInTheDocument();
  });

  it("shows the Super admin a turned-off page, marked as such (ADR 0009)", async () => {
    setFakeFeature("galerie-photos", false);
    renderProtected("gallery", "super_admin");
    expect(await screen.findByText("Contenu protégé")).toBeInTheDocument();
    expect(screen.getByText("Galerie photos — désactivée.")).toBeInTheDocument();
  });

  it("renders a feature-gated entry once its feature is known to be active", async () => {
    renderProtected("gallery");
    expect(await screen.findByText("Contenu protégé")).toBeInTheDocument();
  });
});
