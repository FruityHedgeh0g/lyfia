import { describe, expect, it } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "../../auth/AuthContext";
import { testUser } from "../../test/testUser";
import MediasAdminPage from "./MediasAdminPage";
import { setFakeFeature } from "../../test/fakeApi";

const renderPage = () =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider user={testUser("bureau")}>
        <MediasAdminPage />
      </AuthProvider>
    </QueryClientProvider>
  );

describe("MediasAdminPage", () => {
  it("adds an image with its description", async () => {
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: "+ Ajouter une image" }));
    await userEvent.upload(screen.getByLabelText("Image"), new File(["png"], "depart.png", { type: "image/png" }));
    await userEvent.type(screen.getByLabelText("Description"), "Le départ de la balade");
    await userEvent.click(screen.getByRole("button", { name: "Ajouter" }));

    expect(await screen.findByRole("button", { name: /Le départ de la balade/ })).toHaveTextContent("depart.png");
  });

  it("says uploading is suspended while Dépôt de médias is off, and still describes images (ADR 0009)", async () => {
    setFakeFeature("depot-medias", false);
    renderPage();
    expect(await screen.findByText("Le dépôt de nouvelles images est temporairement suspendu.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "+ Ajouter une image" })).not.toBeInTheDocument();
  });

  it("describes an image again", async () => {
    renderPage();
    const first = (await screen.findAllByRole("button", { name: /Collecte 2025/ }))[0];
    await userEvent.click(first);
    await userEvent.clear(screen.getByLabelText("Description"));
    await userEvent.type(screen.getByLabelText("Description"), "Le convoi au départ");
    await userEvent.click(screen.getByRole("button", { name: "Enregistrer" }));
    await waitFor(() => expect(screen.getByRole("button", { name: /Le convoi au départ/ })).toBeInTheDocument());
  });
});
