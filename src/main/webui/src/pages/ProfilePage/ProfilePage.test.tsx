import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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

  it("shows the account's e-mail, read only, and saves the birth date", async () => {
    renderFor({ ...testUser("benevole", { birthDate: "" })!, email: "jean@example.org" });
    const email = screen.getByLabelText("E-mail");
    expect(email).toHaveValue("jean@example.org");
    expect(email).toBeDisabled();

    await userEvent.type(screen.getByLabelText("Date de naissance"), "1995-04-12");
    await userEvent.click(screen.getByRole("button", { name: "Enregistrer" }));
    expect(await screen.findByText("Vos informations ont été mises à jour.")).toBeInTheDocument();
  });

  it("shows the Super admin account as such only", () => {
    renderFor(testUser("super_admin"));
    expect(screen.getByText("Super admin")).toBeInTheDocument();
    expect(screen.queryByText("Secteur Algrange")).not.toBeInTheDocument();
  });
});
