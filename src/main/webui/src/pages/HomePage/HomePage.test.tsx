import { describe, expect, it } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { AuthProvider } from "../../auth/AuthContext";
import { testUser } from "../../test/testUser";
import { setFakeFeature } from "../../test/fakeApi";
import HomePage from "./HomePage";

const renderAs = (role: string) =>
  render(
    <QueryClientProvider client={new QueryClient()}>
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
