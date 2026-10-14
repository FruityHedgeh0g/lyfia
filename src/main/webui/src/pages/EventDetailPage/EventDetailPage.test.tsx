import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "../../auth/AuthContext";
import { createEventsApi, createRegistrationsApi, seedGroup, setFakeFeature, setFakeFeatureOffIn } from "../../test/fakeApi";
import EventDetailPage from "./EventDetailPage";
import { testUser } from "../../test/testUser";
import { confirmIn } from "../../test/confirm";

const inDays = (days: number) => new Date(Date.now() + days * 86_400_000).toISOString().slice(0, 19);

let eventId: string;

const renderPage = (role = "benevole", phone = "06 12 34 56 78") =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={[`/evenements/${eventId}`]}>
        <AuthProvider user={testUser(role, { phone })}>
          <Routes>
            <Route path="/evenements/:eventId" element={<EventDetailPage />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>
  );

describe("EventDetailPage sign-up", () => {
  beforeEach(async () => {
    const events = createEventsApi();
    const event = await events.createEvent({
      name: "Test Balade",
      description: "",
      sectorId: "sector-1",
      startDateTime: inDays(10),
      endDateTime: inDays(11),
    });
    await events.changeStatus(event.eventId, "ouvert");
    eventId = event.eventId;
  });

  afterEach(() => localStorage.clear());

  it("asks for the phone number, saves it and signs up", async () => {
    renderPage("benevole", "");

    await userEvent.click(await screen.findByRole("button", { name: "M'inscrire comme conducteur" }));
    await userEvent.type(await screen.findByLabelText("Téléphone"), "06 12 34 56 78");
    await userEvent.click(screen.getByRole("button", { name: "Enregistrer et m'inscrire" }));

    expect(await screen.findByText("Vous êtes inscrit comme conducteur")).toBeInTheDocument();
  });

  it("signs up with a chosen Groupe and shows the pending Demande", async () => {
    renderPage();

    await userEvent.selectOptions(await screen.findByLabelText("Groupe (facultatif)"), "group-1");
    await userEvent.click(screen.getByRole("button", { name: "M'inscrire comme conducteur" }));

    expect(await screen.findByText("Demande pour Groupe Algrange Centre : en attente")).toBeInTheDocument();
  });

  it("offers each Poste, with only the Groupes of the kind that goes with it", async () => {
    seedGroup({ groupId: "buvette", name: "Buvette", sectorId: "sector-1", kind: "service" });
    renderPage();

    const groupes = () => [...(screen.getByLabelText("Groupe (facultatif)") as HTMLSelectElement).options].map((o) => o.textContent);
    await screen.findByLabelText("Poste");
    expect(groupes()).toEqual(["Sans groupe", "Groupe Algrange Centre"]);
    await userEvent.selectOptions(screen.getByLabelText("Poste"), "service");
    expect(groupes()).toEqual(["Sans groupe", "Buvette"]);
    await userEvent.selectOptions(screen.getByLabelText("Poste"), "point_fixe");
    expect(screen.queryByLabelText("Groupe (facultatif)")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "M'inscrire comme point fixe" }));
    expect(await screen.findByText("Vous êtes inscrit comme point fixe")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Demander ce groupe" })).not.toBeInTheDocument();
  });

  it("signs up as passager of a chosen conducteur", async () => {
    await createRegistrationsApi().signUp(eventId, { userId: "p-1", firstName: "Marc", lastName: "Weber", phone: "06 00 00 00 00" });
    renderPage();

    await userEvent.selectOptions(await screen.findByLabelText("Poste"), "passager");
    // Sans conducteur, le passager peut demander son propre Groupe ; avec un conducteur, il prend le sien
    expect(screen.getByLabelText("Groupe (facultatif)")).toBeInTheDocument();
    // Le nom se tape : la liste des conducteurs inscrits n'est jamais montrée
    expect(screen.queryByRole("combobox", { name: /conducteur/i })).not.toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("Prénom et nom de votre conducteur (facultatif)"), "weber marc");
    expect(screen.queryByLabelText("Groupe (facultatif)")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "M'inscrire comme passager" }));

    expect(await screen.findByText("Vous êtes inscrit comme passager de Marc Weber")).toBeInTheDocument();
  });

  it("says when no conducteur signed up matches the name typed", async () => {
    await createRegistrationsApi().signUp(eventId, { userId: "p-1", firstName: "Marc", lastName: "Weber", phone: "06 00 00 00 00" });
    renderPage();

    await userEvent.selectOptions(await screen.findByLabelText("Poste"), "passager");
    await userEvent.type(screen.getByLabelText("Prénom et nom de votre conducteur (facultatif)"), "Paul Martin");
    await userEvent.click(screen.getByRole("button", { name: "M'inscrire comme passager" }));

    expect(await screen.findByText(/Aucun conducteur inscrit ces journées-là ne correspond à ce nom/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "M'inscrire comme passager" })).toBeInTheDocument();
  });

  it("signs up as passager without a conducteur for now", async () => {
    renderPage();
    await userEvent.selectOptions(await screen.findByLabelText("Poste"), "passager");
    await userEvent.click(screen.getByRole("button", { name: "M'inscrire comme passager" }));

    expect(await screen.findByText("Vous êtes inscrit comme passager")).toBeInTheDocument();
    expect(screen.getByRole("list", { name: "Mes journées" })).toHaveTextContent("sans conducteur");
  });

  it("signs up for one Journée, then adds the other", async () => {
    renderPage();
    await userEvent.click(await screen.findByLabelText("Tout l'événement"));
    const [first, second] = screen.getAllByRole("checkbox").slice(1);
    await userEvent.click(first);
    await userEvent.click(screen.getByRole("button", { name: "M'inscrire comme conducteur" }));

    const mine = await screen.findByRole("list", { name: "Mes journées" });
    expect(within(mine).getAllByRole("listitem")).toHaveLength(1);
    expect(second).not.toBeInTheDocument();
    await userEvent.click(within(screen.getByRole("group", { name: "Ajouter des journées" })).getByRole("checkbox"));
    await userEvent.click(screen.getByRole("button", { name: "Ajouter ces journées" }));
    await waitFor(() => expect(within(screen.getByRole("list", { name: "Mes journées" })).getAllByRole("listitem")).toHaveLength(2));
  });

  it("withdraws from one Journée and keeps the other", async () => {
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: "M'inscrire comme conducteur" }));
    const mine = await screen.findByRole("list", { name: "Mes journées" });
    await userEvent.click(within(mine).getAllByRole("button", { name: "Me retirer" })[0]);
    await confirmIn("Me retirer");

    await waitFor(() => expect(within(screen.getByRole("list", { name: "Mes journées" })).getAllByRole("listitem")).toHaveLength(1));
    expect(screen.getByRole("button", { name: "Me désinscrire" })).toBeInTheDocument();
  });

  it("says sign-ups are suspended while Inscription aux événements is off (ADR 0009)", async () => {
    setFakeFeature("inscription-evenements", false);
    renderPage();

    expect(await screen.findByText("Les inscriptions en ligne sont temporairement suspendues.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "M'inscrire comme conducteur" })).not.toBeInTheDocument();
  });

  it("says sign-ups are suspended when Inscription aux événements is off for the Event's Secteur only", async () => {
    setFakeFeatureOffIn("inscription-evenements", "sector-1");
    renderPage();

    expect(await screen.findByText("Les inscriptions en ligne sont temporairement suspendues.")).toBeInTheDocument();
  });

  it("still lets a Participant withdraw while Inscription aux événements is off", async () => {
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: "M'inscrire comme conducteur" }));
    expect(await screen.findByText("Vous êtes inscrit comme conducteur")).toBeInTheDocument();
    setFakeFeature("inscription-evenements", false);

    await userEvent.click(screen.getByRole("button", { name: "Me désinscrire" }));
    await confirmIn("Me désinscrire");
    expect(await screen.findByRole("button", { name: "M'inscrire comme conducteur" })).toBeInTheDocument();
  });

  it("keeps the place when the withdrawal is not confirmed", async () => {
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: "M'inscrire comme conducteur" }));
    expect(await screen.findByText("Vous êtes inscrit comme conducteur")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Me désinscrire" }));
    await confirmIn("Annuler");
    expect(screen.getByText("Vous êtes inscrit comme conducteur")).toBeInTheDocument();
  });

  it("keeps the Chef's place in the Groupe they lead, with no way to withdraw", async () => {
    const nord = seedGroup({ name: "Test Nord", sectorId: "sector-1", chef: { userId: "mock-user", firstName: "Jean", lastName: "Dupont" } });
    const registrations = createRegistrationsApi();
    await registrations.signUp(eventId, { userId: "mock-user", firstName: "Jean", lastName: "Dupont", phone: "06 00 00 00 00" });
    await registrations.placeInGroup(eventId, "mock-user", nord);
    renderPage();

    expect(await screen.findByText(/Vous menez Test Nord/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Me désinscrire" })).not.toBeInTheDocument();
  });

  it("reserves another Secteur's Event to its Membres and to Bénévoles", async () => {
    const events = createEventsApi();
    const thionville = await events.createEvent({ name: "Test Thionville", description: "", sectorId: "sector-2", startDateTime: inDays(10), endDateTime: inDays(11) });
    await events.changeStatus(thionville.eventId, "ouvert");
    eventId = thionville.eventId;
    renderPage("membre");

    expect(await screen.findByText(/Réservé aux membres de Secteur Thionville et aux bénévoles/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /M'inscrire/ })).not.toBeInTheDocument();
  });

  it("signs up straight away with a phone number, then withdraws", async () => {
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "M'inscrire comme conducteur" }));
    expect(await screen.findByText("Vous êtes inscrit comme conducteur")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Me désinscrire" }));
    await confirmIn("Me désinscrire");
    expect(await screen.findByRole("button", { name: "M'inscrire comme conducteur" })).toBeInTheDocument();
  });
});
