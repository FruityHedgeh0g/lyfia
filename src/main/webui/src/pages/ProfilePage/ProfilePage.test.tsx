import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { seedGroup } from "../../test/fakeApi";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "../../auth/AuthContext";
import { CurrentUser } from "../../auth/session";
import { testUser } from "../../test/testUser";
import ProfilePage from "./ProfilePage";

const renderFor = (user: CurrentUser | null) =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider user={user}>
        <ProfilePage />
      </AuthProvider>
    </QueryClientProvider>
  );

describe("ProfilePage", () => {
  it("shows an Admin's Role, Secteur and Fonction au Bureau", () => {
    renderFor({ ...testUser("admin")!, bureauFunction: "president" });
    expect(screen.getByText("Admin")).toBeInTheDocument();
    expect(screen.getByText("Secteur Algrange")).toBeInTheDocument();
    expect(screen.getByText("Président")).toBeInTheDocument();
  });

  it("shows an Admin off the Bureau without a Fonction", () => {
    renderFor(testUser("admin"));
    expect(screen.queryByText("Membre du Bureau")).not.toBeInTheDocument();
    expect(screen.queryByText("Président")).not.toBeInTheDocument();
  });

  it("shows the Groupe an Admin leads", async () => {
    const user = testUser("admin")!;
    seedGroup({ name: "Test Nord", sectorId: "sector-1", chef: { userId: user.userId, firstName: user.firstName, lastName: user.lastName } });
    renderFor(user);
    expect(await screen.findByText("Chef de groupe — Test Nord")).toBeInTheDocument();
  });

  it("shows the Super admin account as such only", () => {
    renderFor(testUser("super_admin"));
    expect(screen.getByText("Super admin")).toBeInTheDocument();
    expect(screen.queryByText("Secteur Algrange")).not.toBeInTheDocument();
  });
});
