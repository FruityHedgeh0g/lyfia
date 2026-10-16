import { afterEach, describe, expect, it } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "../../auth/AuthContext";
import { testUser } from "../../test/testUser";
import { seedGalleryPhoto } from "../../test/fakeApi";
import GalleryPage from "./GalleryPage";

const renderAs = (role: string) => {
  const user = testUser(role);
  render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider user={user}>
        <GalleryPage />
      </AuthProvider>
    </QueryClientProvider>
  );
};

const edition = { eventId: "event-edition", name: "Balade de la Rose", startDateTime: "2026-05-10T09:00:00" };

const thumbnails = () => within(screen.getByRole("list")).getAllByRole("img").map((img) => img.getAttribute("alt"));

describe("GalleryPage", () => {
  afterEach(() => localStorage.clear());

  it("shows a visitor only the Publique photos, by Album", async () => {
    seedGalleryPhoto({ sectorId: "sector-1", annee: 2026, alt: "Le départ", visibilite: "publique", event: edition });
    seedGalleryPhoto({ sectorId: "sector-1", annee: 2026, alt: "Entre membres", visibilite: "inscrits" });
    seedGalleryPhoto({ sectorId: "sector-1", annee: 2026, alt: "À trier", visibilite: "brouillon" });
    seedGalleryPhoto({ sectorId: "sector-1", annee: 2025, alt: "L'an dernier", visibilite: "publique" });
    renderAs("visiteur");

    const album = await screen.findByLabelText("Album");
    expect(within(album).getAllByRole("option").map((o) => o.textContent)).toEqual([
      "Secteur Algrange · 2026 (1 photo)",
      "Secteur Algrange · 2025 (1 photo)",
    ]);
    expect(await screen.findByAltText("Le départ")).toBeInTheDocument();
    expect(thumbnails()).toEqual(["Le départ"]);

    await userEvent.selectOptions(album, "sector-1:2025");
    expect(await screen.findByAltText("L'an dernier")).toBeInTheDocument();
  });

  it("shows someone signed in the photos réservées aux Inscrits too", async () => {
    seedGalleryPhoto({ sectorId: "sector-1", annee: 2026, alt: "Le départ", visibilite: "publique" });
    seedGalleryPhoto({ sectorId: "sector-1", annee: 2026, alt: "Entre membres", visibilite: "inscrits" });
    renderAs("benevole");
    expect(await screen.findByAltText("Entre membres")).toBeInTheDocument();
    expect(thumbnails()).toHaveLength(2);
  });

  it("narrows an Album to an Event, and opens a photo large with previous / next", async () => {
    seedGalleryPhoto({ sectorId: "sector-1", annee: 2026, alt: "Le départ", visibilite: "publique", event: edition });
    seedGalleryPhoto({ sectorId: "sector-1", annee: 2026, alt: "L'arrivée", visibilite: "publique", event: edition });
    seedGalleryPhoto({ sectorId: "sector-1", annee: 2026, alt: "Le stand", visibilite: "publique" });
    renderAs("visiteur");
    await screen.findByAltText("Le stand");

    await userEvent.selectOptions(screen.getByLabelText("Événement"), "event-edition");
    expect(thumbnails()).toEqual(["Le départ", "L'arrivée"]);

    await userEvent.click(screen.getByRole("button", { name: "Agrandir : Le départ" }));
    const large = screen.getByRole("dialog", { name: "Le départ" });
    expect(within(large).getByText("1 / 2")).toBeInTheDocument();
    await userEvent.click(within(large).getByRole("button", { name: "Photo suivante" }));
    expect(screen.getByRole("dialog", { name: "L'arrivée" })).toBeInTheDocument();
    await userEvent.keyboard("{ArrowLeft}");
    expect(screen.getByRole("dialog", { name: "Le départ" })).toBeInTheDocument();
    await userEvent.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("says when there is nothing to see yet", async () => {
    seedGalleryPhoto({ sectorId: "sector-1", annee: 2026, alt: "À trier", visibilite: "brouillon" });
    renderAs("visiteur");
    expect(await screen.findByText("Aucune photo publiée pour le moment.")).toBeInTheDocument();
  });

  it("opens on the Album and Event of a link from an Event's page", async () => {
    seedGalleryPhoto({ sectorId: "sector-1", annee: 2026, alt: "Le départ", visibilite: "publique", event: edition });
    seedGalleryPhoto({ sectorId: "sector-1", annee: 2026, alt: "Le stand", visibilite: "publique" });
    seedGalleryPhoto({ sectorId: "sector-1", annee: 2025, alt: "L'an dernier", visibilite: "publique" });
    window.history.pushState({}, "", "/galerie?album=sector-1%3A2026&evenement=event-edition");
    renderAs("visiteur");
    expect(await screen.findByAltText("Le départ")).toBeInTheDocument();
    expect(thumbnails()).toEqual(["Le départ"]);
    expect(screen.getByLabelText("Événement")).toHaveValue("event-edition");
    window.history.pushState({}, "", "/");
  });
});
