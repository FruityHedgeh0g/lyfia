import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "../../../auth/AuthContext";
import { testUser } from "../../../test/testUser";
import { withoutDevPersonas } from "../../../test/fakeApi";
import DevPersonaSwitcher from "./DevPersonaSwitcher";

const renderAs = (role: string) =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider user={testUser(role)}>
        <DevPersonaSwitcher />
      </AuthProvider>
    </QueryClientProvider>
  );

describe("DevPersonaSwitcher", () => {
  it("is offered to whoever is logged in, where the API offers personas", async () => {
    renderAs("benevole");
    expect(await screen.findByLabelText("Persona (dev)")).toHaveValue("benevole");
  });

  it("is not offered where the API does not (production)", async () => {
    withoutDevPersonas();
    renderAs("benevole");
    await waitFor(() => expect(vi.mocked(fetch)).toHaveBeenCalled());
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(screen.queryByLabelText("Persona (dev)")).not.toBeInTheDocument();
  });

  it("is not offered to a Visiteur", () => {
    renderAs("visiteur");
    expect(screen.queryByLabelText("Persona (dev)")).not.toBeInTheDocument();
  });

  it("asks the API for the chosen persona, with a Secteur for the Roles that have one", async () => {
    renderAs("benevole");
    await userEvent.selectOptions(await screen.findByLabelText("Persona (dev)"), "bureau");
    await waitFor(() =>
      expect(vi.mocked(fetch)).toHaveBeenCalledWith("/api/dev/persona", expect.objectContaining({ method: "PUT" }))
    );
  });
});
