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
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <AuthProvider user={testUser(role)}>
        <DevPersonaSwitcher />
      </AuthProvider>
    </QueryClientProvider>
  );

describe("DevPersonaSwitcher", () => {
  it("is offered to a Visiteur, who needs no login", async () => {
    renderAs("visiteur");
    expect(await screen.findByLabelText("Persona (dev)")).toHaveValue("");
  });

  it("is not offered where the API has no personas (production)", async () => {
    withoutDevPersonas();
    renderAs("visiteur");
    await waitFor(() => expect(vi.mocked(fetch)).toHaveBeenCalled());
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(screen.queryByLabelText("Persona (dev)")).not.toBeInTheDocument();
  });

  it("takes a persona, with a Secteur for the Roles that have one, then leaves it", async () => {
    renderAs("visiteur");
    await userEvent.selectOptions(await screen.findByLabelText("Persona (dev)"), "bureau");
    expect(await screen.findByLabelText("Secteur du persona")).toBeInTheDocument();
    expect(screen.getByLabelText("Persona (dev)")).toHaveValue("bureau");

    await userEvent.selectOptions(screen.getByLabelText("Persona (dev)"), "");
    await waitFor(() => expect(screen.queryByLabelText("Secteur du persona")).not.toBeInTheDocument());
  });
});
