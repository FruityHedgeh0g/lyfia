import { describe, expect, it } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "../../../auth/AuthContext";
import { testUser } from "../../../test/testUser";
import AdminLayout from "../AdminLayout/AdminLayout";

const renderAdmin = (role: string) =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={["/administration/membres"]}>
        <AuthProvider user={testUser(role)}>
          <Routes>
            <Route path="/administration" element={<AdminLayout />}>
              <Route path="membres" element={<p>Page des inscrits</p>} />
            </Route>
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>
  );

describe("SpaceLayout", () => {
  it("groups the Super admin's tabs by theme, naming the page open", async () => {
    renderAdmin("super_admin");
    const nav = await screen.findByRole("navigation", { name: "Administration" });
    const tabs = await within(nav).findAllByRole("button");
    expect(tabs.map((t) => t.textContent)).toEqual(["Organisation · Inscrits", "Contenu", "Site"]);

    await userEvent.click(within(nav).getByRole("button", { name: "Site" }));
    expect(within(nav).getAllByRole("menuitem").map((m) => m.textContent)).toEqual(["Configuration", "Fonctionnalités", "Journal"]);
  });

  it("shows the Bureau only the themes it works in", async () => {
    renderAdmin("bureau");
    const nav = await screen.findByRole("navigation", { name: "Administration" });
    expect((await within(nav).findAllByRole("button")).map((t) => t.textContent)).toEqual(["Organisation · Inscrits", "Contenu"]);
  });
});
