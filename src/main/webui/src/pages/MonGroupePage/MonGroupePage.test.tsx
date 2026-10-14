import { afterEach, describe, expect, it } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "../../auth/AuthContext";
import { testUser } from "../../test/testUser";
import { createEventsApi, createRegistrationsApi } from "../../test/fakeApi";
import { seedGroup } from "../../test/fakeApi";
import MonGroupePage from "./MonGroupePage";
import { confirmIn } from "../../test/confirm";

/** Une date dans `days` jours, `AAAA-MM-JJ`. */
const inDays = (days: number) => new Date(Date.now() + days * 86_400_000).toISOString().slice(0, 10);

const renderPage = () => {
  const user = testUser("chef_de_groupe");
  render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider user={user}>
        <MonGroupePage />
      </AuthProvider>
    </QueryClientProvider>
  );
};

/** Donne au Chef ("mock-user") un Groupe, et inscrit Sophie avec une Demande pour ce Groupe ; renvoie le Groupe. */
const seedLedGroupe = async () => {
  const nord = seedGroup({ name: "Test Nord", sectorId: "sector-1", chef: { userId: "mock-user", firstName: "Jean", lastName: "Dupont" } });

  const events = createEventsApi();
  // Une seule Journée
  const event = await events.createEvent({
    name: "Test Balade",
    description: "",
    sectorId: "sector-1",
    startDateTime: `${inDays(10)}T09:00:00`,
    endDateTime: `${inDays(10)}T18:00:00`,
  });
  await events.changeStatus(event.eventId, "ouvert");
  await createRegistrationsApi().signUp(event.eventId, { userId: "p-1", firstName: "Sophie", lastName: "Kremer", phone: "06 00 00 00 00" }, nord);
  return nord;
};

describe("MonGroupePage", () => {
  afterEach(() => localStorage.clear());

  it("says so when the Chef leads no Groupe", async () => {
    renderPage();
    expect(await screen.findByText("Vous ne menez aucun groupe pour le moment.")).toBeInTheDocument();
  });

  it("shows pending Demandes per Event and accepts one into the Groupe", async () => {
    await seedLedGroupe();
    renderPage();

    expect(await screen.findByRole("heading", { name: /Test Nord/ })).toBeInTheDocument();
    const balade = await screen.findByRole("region", { name: "Test Balade" });
    await userEvent.click(within(within(balade).getByRole("list", { name: "Demandes en attente" })).getByRole("button", { name: "Accepter" }));

    await waitFor(() => expect(within(within(balade).getByRole("list", { name: "Membres du groupe" })).getByText(/Kremer/)).toBeInTheDocument());
    expect(within(balade).queryByRole("list", { name: "Demandes en attente" })).not.toBeInTheDocument();
  });

  it("shows the Groupe's maximum and, once reached, keeps Demandes waiting in sign-up order", async () => {
    const nord = await seedLedGroupe();
    const eventId = (await createEventsApi().fetchEvents(true)).find((e) => e.name === "Test Balade")!.eventId;
    const api = createRegistrationsApi();
    await api.setGroupMaximum(eventId, nord, 1);
    await api.signUp(eventId, { userId: "p-2", firstName: "Marc", lastName: "Weber", phone: "06 00 00 00 00" }, nord);
    await api.decideDemande(eventId, "p-2", { personId: "mock-user", bureau: false }, true);
    await api.signUp(eventId, { userId: "p-3", firstName: "Lea", lastName: "Meyer", phone: "06 00 00 00 00" }, nord);
    renderPage();

    const balade = await screen.findByRole("region", { name: "Test Balade" });
    expect(within(balade).getByText("Dans le groupe : 1 / 1")).toBeInTheDocument();
    const demandes = within(balade).getByRole("list", { name: "Demandes en attente" });
    expect(within(demandes).getAllByRole("listitem").map((li) => li.textContent)).toEqual([
      expect.stringContaining("Kremer"),
      expect.stringContaining("Meyer"),
    ]);
    expect(within(demandes).getAllByRole("button", { name: "Accepter" })[0]).toBeDisabled();
    expect(within(balade).getByText(/Maximum atteint/)).toBeInTheDocument();

    await userEvent.click(within(within(balade).getByRole("list", { name: "Membres du groupe" })).getByRole("button", { name: "Sortir du groupe" }));
    await confirmIn("Sortir du groupe");
    await waitFor(() => expect(within(balade).getByText("Dans le groupe : 0 / 1")).toBeInTheDocument());
    expect(within(within(balade).getByRole("list", { name: "Demandes en attente" })).getAllByRole("listitem")).toHaveLength(2);
  });

  it("keeps the Chef in the Groupe they lead", async () => {
    const nord = await seedLedGroupe();
    const eventId = (await createEventsApi().fetchEvents(true)).find((e) => e.name === "Test Balade")!.eventId;
    const registrations = createRegistrationsApi();
    await registrations.signUp(eventId, { userId: "mock-user", firstName: "Jean", lastName: "Dupont", phone: "06 00 00 00 00" });
    await registrations.placeInGroup(eventId, "mock-user", nord);
    renderPage();

    const balade = await screen.findByRole("region", { name: "Test Balade" });
    const members = await within(balade).findByRole("list", { name: "Membres du groupe" });
    const chef = within(members).getByText(/Jean Dupont/).closest("li") as HTMLElement;
    expect(within(chef).queryByRole("button", { name: "Sortir du groupe" })).not.toBeInTheDocument();
  });

  it("shows each Journée of a longer Event and pairs a passager with a conducteur of the Groupe", async () => {
    const nord = seedGroup({ name: "Test Nord", sectorId: "sector-1", chef: { userId: "mock-user", firstName: "Jean", lastName: "Dupont" } });
    const events = createEventsApi();
    const event = await events.createEvent({
      name: "Test Week-end",
      description: "",
      sectorId: "sector-1",
      startDateTime: `${inDays(10)}T09:00:00`,
      endDateTime: `${inDays(11)}T18:00:00`,
    });
    await events.changeStatus(event.eventId, "ouvert");
    const api = createRegistrationsApi();
    await api.signUp(event.eventId, { userId: "p-2", firstName: "Marc", lastName: "Weber", phone: "06 00 00 00 00" }, nord);
    await api.signUp(event.eventId, { userId: "p-1", firstName: "Sophie", lastName: "Kremer", phone: "06 00 00 00 00" }, nord, undefined, "passager");
    for (const personId of ["p-1", "p-2"]) await api.decideDemande(event.eventId, personId, { personId: "mock-user", bureau: false }, true);
    renderPage();

    const weekEnd = await screen.findByRole("region", { name: "Test Week-end" });
    const lists = await within(weekEnd).findAllByRole("list", { name: /Membres du groupe — / });
    expect(lists).toHaveLength(2);
    const kremer = () =>
      within(within(weekEnd).getAllByRole("list", { name: /Membres du groupe — / })[0]).getByText("Sophie Kremer").closest("li") as HTMLElement;
    expect(kremer()).toHaveTextContent("Passager · sans conducteur");

    await userEvent.selectOptions(within(kremer()).getByLabelText("Conducteur"), "p-2");
    await waitFor(() => expect(kremer()).toHaveTextContent("Passager de Marc Weber"));
    expect(within(within(weekEnd).getAllByRole("list", { name: /Membres du groupe — / })[1]).getByText("Sophie Kremer").closest("li")).toHaveTextContent(
      "sans conducteur"
    );
  });

  it("takes a member out of the Groupe", async () => {
    await seedLedGroupe();
    renderPage();
    const balade = await screen.findByRole("region", { name: "Test Balade" });
    await userEvent.click(within(within(balade).getByRole("list", { name: "Demandes en attente" })).getByRole("button", { name: "Accepter" }));

    const members = await within(balade).findByRole("list", { name: "Membres du groupe" });
    await userEvent.click(within(members).getByRole("button", { name: "Sortir du groupe" }));
    await confirmIn("Sortir du groupe");
    await waitFor(() => expect(within(balade).getByText("Aucun membre pour cet événement.")).toBeInTheDocument());
  });
});
