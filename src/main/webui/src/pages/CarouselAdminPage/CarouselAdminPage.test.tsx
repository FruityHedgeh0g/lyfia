import { afterEach, describe, expect, it } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "../../auth/AuthContext";
import { testUser } from "../../test/testUser";
import { seedGalleryPhoto } from "../../test/fakeApi";
import CarouselAdminPage from "./CarouselAdminPage";

const renderAs = (role: string) => {
  const user = testUser(role);
  render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider user={user}>
        <CarouselAdminPage />
      </AuthProvider>
    </QueryClientProvider>
  );
};

const thisYear = new Date().getFullYear();

describe("CarouselAdminPage", () => {
  afterEach(() => localStorage.clear());

  it("takes a new slide's image from the Galerie, among its Publique photos only", async () => {
    seedGalleryPhoto({ photoId: "photo-depart", sectorId: "sector-1", annee: thisYear, alt: "Le départ", visibilite: "publique" });
    seedGalleryPhoto({ sectorId: "sector-1", annee: thisYear, alt: "À trier", visibilite: "brouillon" });
    renderAs("bureau");

    await userEvent.click(await screen.findByRole("button", { name: "+ Nouvel élément" }));
    await userEvent.type(screen.getByLabelText("Titre"), "La balade");
    expect(screen.getByText("Aucune image")).toBeInTheDocument();
    expect(screen.getByText(/idéalement 16:9, 1920 px/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Choisir dans la Galerie" }));
    const dialog = screen.getByRole("dialog", { name: "Choisir dans la Galerie" });
    expect(await within(dialog).findByRole("button", { name: "Choisir : Le départ" })).toBeInTheDocument();
    expect(within(dialog).queryByRole("button", { name: "Choisir : À trier" })).not.toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Choisir : Le départ" }));

    expect(screen.getByAltText("Aperçu de l'image choisie")).toHaveAttribute("src", expect.stringContaining("media-photo-depart"));
    await userEvent.click(screen.getByRole("button", { name: "Créer l'élément" }));
    await waitFor(() => expect(screen.getByRole("button", { name: /La balade/ })).toBeInTheDocument());
  });

  it("deposits a slide's image on the spot", async () => {
    renderAs("bureau");
    await userEvent.click(await screen.findByRole("button", { name: "+ Nouvel élément" }));
    await userEvent.upload(screen.getByLabelText("Déposer une image : Image"), new File(["image"], "affiche.jpg", { type: "image/jpeg" }));
    expect(await screen.findByAltText("Aperçu de l'image choisie")).toHaveAttribute("src", expect.stringContaining("/api/medias/"));

    await userEvent.click(screen.getByRole("button", { name: "Retirer" }));
    expect(screen.getByText("Aucune image")).toBeInTheDocument();
  });
});
