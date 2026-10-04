import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { AuthProvider } from "../../auth/AuthContext";
import { testUser } from "../../test/testUser";
import { setFakeFeature } from "../../test/fakeApi";
import HomePage from "./HomePage";

const renderAs = (role: string, client = new QueryClient()) =>
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <AuthProvider user={testUser(role)}>
          <HomePage />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>
  );

describe("HomePage carousel", () => {
  it("shows the carousel while Carrousel is on", async () => {
    renderAs("visiteur");
    expect(await screen.findByRole("region", { name: "Carrousel" })).toBeInTheDocument();
  });

  it("hides it from the public while Carrousel is off (ADR 0009)", async () => {
    setFakeFeature("carrousel", false);
    renderAs("visiteur");
    await waitFor(() => expect(screen.getByText("Qui sommes-nous ?")).toBeInTheDocument());
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(screen.queryByRole("region", { name: "Carrousel" })).not.toBeInTheDocument();
  });

  it("shows it to the Super admin, marked as turned off", async () => {
    setFakeFeature("carrousel", false);
    renderAs("super_admin");
    expect(await screen.findByText("Carrousel — désactivée.")).toBeInTheDocument();
    expect(await screen.findByRole("region", { name: "Carrousel" })).toBeInTheDocument();
  });
});

describe("HomePage registration", () => {
  it("offers Je m'engage while Inscription sur le site is on", async () => {
    renderAs("visiteur");
    expect(await screen.findByText("Devenir bénévole")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Je m'engage" })).toBeInTheDocument();
  });

  it("hides Je m'engage while Inscription sur le site is off (ADR 0009)", async () => {
    setFakeFeature("inscription-site", false);
    renderAs("visiteur");
    await waitFor(() => expect(screen.getByText("Faire un don", { selector: "h3" })).toBeInTheDocument());
    await waitFor(() => expect(screen.queryByText("Devenir bénévole")).not.toBeInTheDocument());
    expect(screen.queryByRole("link", { name: "Je m'engage" })).not.toBeInTheDocument();
  });

  it("still offers Je m'engage when the Fonctionnalités cannot be read", async () => {
    const realFetch = fetch;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL, init?: RequestInit) =>
      String(input).startsWith("/api/features") ? Promise.resolve(new Response(null, { status: 500 })) : realFetch(input, init)
    ));
    renderAs("visiteur", new QueryClient({ defaultOptions: { queries: { retry: false } } }));
    expect(await screen.findByText("Devenir bénévole")).toBeInTheDocument();
  });
});
