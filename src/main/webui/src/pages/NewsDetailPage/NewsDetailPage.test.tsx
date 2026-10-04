import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "../../auth/AuthContext";
import { testUser } from "../../test/testUser";
import { setFakeFeatureOffIn } from "../../test/fakeApi";
import NewsDetailPage from "./NewsDetailPage";

const renderPost = (postId: string) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={[`/actualites/${postId}`]}>
        <AuthProvider user={testUser("visiteur")}>
          <Routes>
            <Route path="/actualites/:postId" element={<NewsDetailPage />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>
  );

describe("NewsDetailPage", () => {
  it("shows a published Post", async () => {
    renderPost("post-1");
    expect(await screen.findByRole("heading", { name: "Retour sur la collecte 2025" })).toBeInTheDocument();
  });

  it("is unavailable while Actualités is off for the Post's Secteur (ADR 0009)", async () => {
    setFakeFeatureOffIn("actualites", "sector-1");
    renderPost("post-1");
    expect(await screen.findByText("Fonctionnalité temporairement indisponible")).toBeInTheDocument();
  });
});
