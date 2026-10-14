import { afterEach, describe, expect, it } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "../../auth/AuthContext";
import { testUser } from "../../test/testUser";
import ConfigurationPage from "./ConfigurationPage";

const renderPage = () =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider user={testUser("super_admin")}>
        <ConfigurationPage />
      </AuthProvider>
    </QueryClientProvider>
  );

describe("ConfigurationPage", () => {
  afterEach(() => localStorage.clear());

  it("lets the Super admin upload the décharge des mineurs, as a PDF", async () => {
    renderPage();
    const section = await screen.findByRole("region", { name: "Décharge des mineurs" });
    expect(await within(section).findByText("Aucune décharge déposée.")).toBeInTheDocument();

    const pdf = new File(["%PDF-1.4"], "decharge-2026.pdf", { type: "application/pdf" });
    await userEvent.upload(within(section).getByLabelText("Nouvelle version (PDF)"), pdf);
    await userEvent.click(within(section).getByRole("button", { name: "Déposer" }));

    expect(await within(section).findByRole("link", { name: "decharge-2026.pdf" })).toBeInTheDocument();
  });
});
