import { describe, expect, it } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "../../auth/AuthContext";
import { testUser } from "../../test/testUser";
import FeatureFlagsPage from "./FeatureFlagsPage";

const renderAs = (role: string) =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider user={testUser(role)}>
        <FeatureFlagsPage />
      </AuthProvider>
    </QueryClientProvider>
  );

describe("FeatureFlagsPage", () => {
  it("lets the Super admin switch one on", async () => {
    renderAs("super_admin");
    await userEvent.click(await screen.findByRole("button", { name: "Activer" }));
    await waitFor(() => expect(screen.queryByRole("button", { name: "Activer" })).not.toBeInTheDocument());
  });
});
