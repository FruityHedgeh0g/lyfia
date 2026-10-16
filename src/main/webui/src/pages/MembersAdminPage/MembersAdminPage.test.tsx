import { afterEach, describe, expect, it } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "../../auth/AuthContext";
import { testUser } from "../../test/testUser";
import MembersAdminPage from "./MembersAdminPage";

const renderAs = (role: string) => {
  const user = testUser(role);
  render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider user={user}>
        <MembersAdminPage />
      </AuthProvider>
    </QueryClientProvider>
  );
};

const bureauOptions = () =>
  within(screen.getByLabelText("Bureau")).getAllByRole("option").map((o) => o.textContent);

const roleOptions = () =>
  within(screen.getByLabelText("Rôle")).getAllByRole("option").map((o) => o.textContent);

describe("MembersAdminPage", () => {
  afterEach(() => {
    localStorage.clear();
  });

  it("shows each person's contact details in the unfolded card, not under the name", async () => {
    renderAs("bureau");
    const sophie = await screen.findByRole("button", { name: /Sophie Kremer/ });
    expect(sophie).not.toHaveTextContent("06 23 45 67 89");
    await userEvent.click(sophie);
    expect(screen.getByLabelText("Téléphone")).toHaveValue("06 23 45 67 89");
    expect(screen.getByLabelText("E-mail")).toHaveValue("sophie.kremer@urue-algrange.fr");
    expect(screen.getByLabelText("Téléphone")).toHaveAttribute("readonly");
    expect(screen.getByLabelText("Date de naissance")).toHaveValue("");
    expect(screen.getByLabelText("Date de naissance")).toHaveAttribute("placeholder", "Non renseigné");
  });

  it("finds an Inscrit by phone or e-mail", async () => {
    renderAs("bureau");
    await screen.findByRole("button", { name: /Sophie Kremer/ });
    await userEvent.type(screen.getByLabelText("Rechercher un inscrit"), "sophie.kremer@");
    expect(screen.getByRole("button", { name: /Sophie Kremer/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Julien Meyer/ })).not.toBeInTheDocument();
  });

  it("filters the Inscrits by any text of their line, ignoring accents", async () => {
    renderAs("bureau");
    await screen.findByRole("button", { name: /Sophie Kremer/ });
    await userEvent.type(screen.getByLabelText("Rechercher un inscrit"), "benevole kremer");
    expect(screen.getByRole("button", { name: /Sophie Kremer/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Julien Meyer/ })).not.toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText("Rechercher un inscrit"));
    await userEvent.type(screen.getByLabelText("Rechercher un inscrit"), "personne inconnue");
    expect(screen.getByText(/Aucun résultat/)).toBeInTheDocument();
  });

  it("offers the Bureau only membre and chef_de_groupe", async () => {
    renderAs("bureau");
    await userEvent.click(await screen.findByRole("button", { name: /Sophie Kremer/ }));
    expect(roleOptions()).toEqual(["Bénévole", "Membre", "Chef de groupe"]);
  });

  it("offers an Admin the bureau Role too", async () => {
    renderAs("admin");
    await userEvent.click(await screen.findByRole("button", { name: /Sophie Kremer/ }));
    expect(roleOptions()).toEqual(["Bénévole", "Membre", "Chef de groupe", "Bureau"]);
  });

  it("gives nobody a permanent Groupe: who rides with a Groupe is decided per Event", async () => {
    renderAs("bureau");
    const sophie = await screen.findByRole("button", { name: /Sophie Kremer/ });
    expect(sophie).not.toHaveTextContent("Groupe Algrange Centre");
    await userEvent.click(sophie);
    expect(screen.queryByLabelText("Groupe")).not.toBeInTheDocument();
  });

  it("offers no promotion on a person at the viewer's level", async () => {
    renderAs("bureau");
    await userEvent.click(await screen.findByRole("button", { name: /Claire Hoffmann/ }));
    expect(screen.queryByLabelText("Rôle")).not.toBeInTheDocument();
  });

  it("promotes a Bénévole to Membre", async () => {
    renderAs("bureau");
    await userEvent.click(await screen.findByRole("button", { name: /Sophie Kremer/ }));
    await userEvent.selectOptions(screen.getByLabelText("Rôle"), "membre");
    await userEvent.click(screen.getByRole("button", { name: "Enregistrer" }));
    await waitFor(() => expect(screen.getByRole("button", { name: /Sophie Kremer/ })).toHaveTextContent("Membre"));
  });

  it("shows the Secteur of a new Membre, given by the promoting Bureau member", async () => {
    renderAs("bureau");
    await userEvent.click(await screen.findByRole("button", { name: /Sophie Kremer/ }));
    await userEvent.selectOptions(screen.getByLabelText("Rôle"), "membre");
    await userEvent.click(screen.getByRole("button", { name: "Enregistrer" }));
    await waitFor(() => expect(screen.getByRole("button", { name: /Sophie Kremer/ })).toHaveTextContent("Secteur Algrange"));
  });

  it("asks the Super admin for the Secteur of a new Admin", async () => {
    renderAs("super_admin");
    await userEvent.click(await screen.findByRole("button", { name: /Sophie Kremer/ }));
    expect(screen.queryByLabelText("Secteur")).not.toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText("Rôle"), "admin");
    await userEvent.selectOptions(screen.getByLabelText("Secteur"), "sector-2");
    await userEvent.click(screen.getByRole("button", { name: "Enregistrer" }));
    await waitFor(() => expect(screen.getByRole("button", { name: /Sophie Kremer/ })).toHaveTextContent("Secteur Thionville"));
  });

  it("shows the Président", async () => {
    renderAs("bureau");
    expect(await screen.findByRole("button", { name: /Claire Hoffmann/ })).toHaveTextContent("Président");
    expect(screen.getByRole("button", { name: /Luc Schmitt/ })).not.toHaveTextContent("Président");
  });

  it("lets an Admin make another Bureau member Président", async () => {
    renderAs("admin");
    await userEvent.click(await screen.findByRole("button", { name: /Luc Schmitt/ }));
    await userEvent.selectOptions(screen.getByLabelText("Bureau"), "president");
    await userEvent.click(screen.getByRole("button", { name: "Enregistrer" }));
    await waitFor(() => expect(screen.getByRole("button", { name: /Luc Schmitt/ })).toHaveTextContent("Président"));
  });

  it("keeps a Bureau member on the Bureau: no choice to leave it", async () => {
    renderAs("admin");
    await userEvent.click(await screen.findByRole("button", { name: /Luc Schmitt/ }));
    expect(bureauOptions()).toEqual(["Membre du Bureau", "Président"]);
  });

  it("lets an Admin call another Admin to the Bureau", async () => {
    renderAs("admin");
    const paul = await screen.findByRole("button", { name: /Paul Muller/ });
    expect(paul).not.toHaveTextContent("Bureau");
    await userEvent.click(paul);
    expect(bureauOptions()).toEqual(["—", "Membre du Bureau", "Président"]);
    await userEvent.selectOptions(screen.getByLabelText("Bureau"), "membre_du_bureau");
    await userEvent.click(screen.getByRole("button", { name: "Enregistrer" }));
    await waitFor(() => expect(screen.getByRole("button", { name: /Paul Muller/ })).toHaveTextContent("Admin · Bureau"));
  });

  it("makes someone promoted to the Bureau Role a Membre du Bureau", async () => {
    renderAs("admin");
    await userEvent.click(await screen.findByRole("button", { name: /Sophie Kremer/ }));
    expect(screen.queryByLabelText("Bureau")).not.toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText("Rôle"), "bureau");
    expect(screen.getByLabelText("Bureau")).toHaveValue("membre_du_bureau");
  });

  it("lets an Admin promote a Membre to the Bureau", async () => {
    renderAs("admin");
    await userEvent.click(await screen.findByRole("button", { name: /Camille Muller/ }));
    await userEvent.selectOptions(screen.getByLabelText("Rôle"), "bureau");
    await userEvent.click(screen.getByRole("button", { name: "Enregistrer" }));
    await waitFor(() => expect(screen.getByRole("button", { name: /Camille Muller/ })).toHaveTextContent("Bureau"));
  });

  it("offers the Fonction au Bureau only to an Admin, and never on themselves", async () => {
    renderAs("bureau");
    await userEvent.click(await screen.findByRole("button", { name: /Luc Schmitt/ }));
    expect(screen.queryByLabelText("Bureau")).not.toBeInTheDocument();
  });

  it("lets only an Admin correct someone's names", async () => {
    renderAs("bureau");
    await userEvent.click(await screen.findByRole("button", { name: /Sophie Kremer/ }));
    expect(screen.getByLabelText("Prénom")).toBeDisabled();
  });

  it("lets an Admin correct someone's names", async () => {
    renderAs("admin");
    await userEvent.click(await screen.findByRole("button", { name: /Sophie Kremer/ }));
    await userEvent.clear(screen.getByLabelText("Prénom"));
    await userEvent.type(screen.getByLabelText("Prénom"), "Sophia");
    await userEvent.click(screen.getByRole("button", { name: "Enregistrer" }));
    expect(await screen.findByRole("button", { name: /Sophia Kremer/ })).toBeInTheDocument();
  });
});
