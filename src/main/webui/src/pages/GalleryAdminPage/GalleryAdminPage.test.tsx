import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "../../auth/AuthContext";
import { testUser } from "../../test/testUser";
import { createEventsApi, seedGalleryPhoto, setFakeFeature } from "../../test/fakeApi";
import { confirmIn } from "../../test/confirm";
import GalleryAdminPage from "./GalleryAdminPage";

// jsdom ne dessine pas d'image : la préparation par le navigateur (ADR 0014) rend le fichier tel quel
vi.mock("../../features/galerie/resize", () => ({
  preparePhoto: async (file: File) => ({ name: file.name, original: file, affichage: file, vignette: file }),
}));

const renderAs = (role: string) => {
  const user = testUser(role);
  render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider user={user}>
        <GalleryAdminPage />
      </AuthProvider>
    </QueryClientProvider>
  );
};

const thisYear = new Date().getFullYear();
/** Les Visibilités affichées sur les vignettes. */
const badges = (label: string) => within(screen.getAllByRole("list").at(-1)!).queryAllByText(label);

const jpeg = (name: string) => new File(["photo"], name, { type: "image/jpeg" });

let editionId = "";

describe("GalleryAdminPage", () => {
  beforeEach(async () => {
    const edition = await createEventsApi().createEvent({
      name: "Balade de la Rose",
      description: "",
      sectorId: "sector-1",
      startDateTime: `${thisYear}-05-10T09:00:00`,
      endDateTime: `${thisYear}-05-10T18:00:00`,
    });
    editionId = edition.eventId;
  });
  afterEach(() => localStorage.clear());

  it("shows the Bureau its Secteur's photos with their Visibilité, Brouillons included", async () => {
    seedGalleryPhoto({ sectorId: "sector-1", annee: thisYear, alt: "À trier", visibilite: "brouillon" });
    seedGalleryPhoto({ sectorId: "sector-1", annee: thisYear, alt: "En ligne", visibilite: "publique" });
    seedGalleryPhoto({ sectorId: "sector-2", annee: thisYear, alt: "Ailleurs", visibilite: "brouillon" });
    renderAs("bureau");

    expect(await screen.findByAltText("À trier")).toBeInTheDocument();
    expect(screen.getByAltText("En ligne")).toBeInTheDocument();
    expect(screen.queryByAltText("Ailleurs")).not.toBeInTheDocument();
    expect(badges("Brouillon")).toHaveLength(1);
  });

  it("makes a selection of photos Publique at once", async () => {
    seedGalleryPhoto({ sectorId: "sector-1", annee: thisYear, alt: "Première", visibilite: "brouillon" });
    seedGalleryPhoto({ sectorId: "sector-1", annee: thisYear, alt: "Seconde", visibilite: "brouillon" });
    renderAs("bureau");
    await screen.findByAltText("Première");

    await userEvent.click(screen.getByRole("button", { name: "Tout sélectionner" }));
    expect(screen.getByText("2 sélectionnée(s)")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Appliquer" }));

    await waitFor(() => expect(badges("Publique")).toHaveLength(2));
    expect(badges("Brouillon")).toHaveLength(0);
  });

  it("deposits a batch of an Event's photos, which land as Brouillons in its Album", async () => {
    renderAs("bureau");
    await userEvent.click(await screen.findByRole("button", { name: "Déposer des photos" }));
    const dialog = screen.getByRole("dialog", { name: "Déposer des photos" });
    await userEvent.upload(within(dialog).getByLabelText("Photos (50 au plus)"), [jpeg("depart.jpg"), jpeg("arrivee.jpg")]);
    await userEvent.selectOptions(within(dialog).getByLabelText("Événement"), editionId);
    await userEvent.click(within(dialog).getByLabelText("Groupe Algrange Centre"));
    await userEvent.type(within(dialog).getByLabelText("Description commune (facultative)"), "La balade");
    await waitFor(() => expect(within(dialog).getByRole("button", { name: "Déposer" })).toBeEnabled());
    await userEvent.click(within(dialog).getByRole("button", { name: "Déposer" }));

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(await screen.findAllByAltText("La balade")).toHaveLength(2);
    expect(badges("Brouillon")).toHaveLength(2);
  });

  it("describes a photo, then deletes it once confirmed", async () => {
    seedGalleryPhoto({ sectorId: "sector-1", annee: thisYear, alt: "Le départ", visibilite: "brouillon" });
    renderAs("bureau");
    await userEvent.click(await screen.findByRole("button", { name: "Agrandir : Le départ" }));
    const dialog = screen.getByRole("dialog", { name: "Photo de la Galerie" });
    const description = within(dialog).getByLabelText("Description");
    await userEvent.clear(description);
    await userEvent.type(description, "Le grand départ");
    await userEvent.click(within(dialog).getByRole("button", { name: "Enregistrer" }));
    expect(await screen.findByAltText("Le grand départ")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Agrandir : Le grand départ" }));
    await userEvent.click(within(screen.getByRole("dialog", { name: "Photo de la Galerie" })).getByRole("button", { name: "Supprimer" }));
    await confirmIn("Supprimer");
    expect(await screen.findByText("Aucune photo dans cet album.")).toBeInTheDocument();
  });

  it("deposits nothing while Dépôt de médias is off", async () => {
    setFakeFeature("depot-medias", false);
    renderAs("bureau");
    expect(await screen.findByText("Le dépôt de nouvelles photos est temporairement suspendu.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Déposer des photos" })).not.toBeInTheDocument();
  });

  it("keeps the images used outside the Galerie in their own tab", async () => {
    renderAs("bureau");
    await userEvent.click(await screen.findByRole("tab", { name: "Autres images" }));
    expect(await screen.findByRole("heading", { name: "Autres images" })).toBeInTheDocument();
  });

  it("validates a Chef de groupe's Proposition with its Visibilité, or refuses one", async () => {
    seedGalleryPhoto({ sectorId: "sector-1", annee: thisYear, alt: "Proposée", visibilite: "brouillon", proposition: true, depositedBy: "chef-1" });
    seedGalleryPhoto({ sectorId: "sector-1", annee: thisYear, alt: "Hors sujet", visibilite: "brouillon", proposition: true, depositedBy: "chef-1" });
    renderAs("bureau");

    const queue = await screen.findByRole("region", { name: "Photos à valider" });
    const [first, second] = within(queue).getAllByRole("listitem");
    await userEvent.selectOptions(within(first).getByLabelText("Visibilité"), "inscrits");
    await userEvent.click(within(first).getByRole("button", { name: "Valider" }));
    await waitFor(() => expect(within(screen.getByRole("region", { name: "Photos à valider" })).getAllByRole("listitem")).toHaveLength(1));

    await userEvent.click(within(second).getByRole("button", { name: "Refuser" }));
    await confirmIn("Refuser");
    await waitFor(() => expect(screen.queryByRole("region", { name: "Photos à valider" })).not.toBeInTheDocument());
    expect(screen.queryByAltText("Hors sujet")).not.toBeInTheDocument();
    expect(screen.getByAltText("Proposée")).toBeInTheDocument();
  });
});
