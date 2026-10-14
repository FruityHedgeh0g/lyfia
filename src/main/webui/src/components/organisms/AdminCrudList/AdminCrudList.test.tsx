import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AdminCrudList from "./AdminCrudList";
import FormField from "../../molecules/FormField/FormField";

interface Item {
  id: string;
  name: string;
}

const items: Item[] = [
  { id: "a", name: "Alpha" },
  { id: "b", name: "Bravo" },
];

const renderList = (overrides: Partial<React.ComponentProps<typeof AdminCrudList<Item, string>>> = {}) => {
  const onUpdate = vi.fn().mockResolvedValue(undefined);
  const onCreate = vi.fn().mockResolvedValue(undefined);
  const onRemove = vi.fn().mockResolvedValue(undefined);
  render(
    <AdminCrudList<Item, string>
      title="Éléments"
      items={items}
      idOf={(i) => i.id}
      display={(i) => ({ title: i.name })}
      toDraft={(i) => i.name}
      renderFields={(draft, setDraft) => <FormField label="Nom" value={draft} onChange={(e) => setDraft(e.target.value)} />}
      onUpdate={onUpdate}
      create={{ buttonLabel: "+ Nouveau", submitLabel: "Créer", emptyDraft: "", onCreate }}
      remove={{ title: "Supprimer ?", message: (i) => `Supprimer ${i.name}`, onRemove }}
      {...overrides}
    />
  );
  return { onUpdate, onCreate, onRemove };
};

describe("AdminCrudList", () => {
  it("edits an item from its current values and closes on success", async () => {
    const { onUpdate } = renderList();
    await userEvent.click(screen.getByRole("button", { name: "Bravo" }));
    const field = screen.getByLabelText("Nom");
    expect(field).toHaveValue("Bravo");
    await userEvent.clear(field);
    await userEvent.type(field, "Bravo bis");
    await userEvent.click(screen.getByRole("button", { name: "Enregistrer" }));
    expect(onUpdate).toHaveBeenCalledWith("b", "Bravo bis");
    await waitFor(() => expect(screen.queryByLabelText("Nom")).not.toBeInTheDocument());
  });

  it("opens and closes an item from its chevron too", async () => {
    renderList();
    const chevron = screen.getByRole("button", { name: "Bravo" }).closest("li")!.querySelector("[aria-hidden='true']")!;
    await userEvent.click(chevron);
    expect(screen.getByLabelText("Nom")).toHaveValue("Bravo");
    await userEvent.click(chevron);
    expect(screen.queryByLabelText("Nom")).not.toBeInTheDocument();
  });

  it("keeps the form open with the input when saving fails", async () => {
    renderList({ onUpdate: vi.fn().mockRejectedValue(new Error("boom")) });
    await userEvent.click(screen.getByRole("button", { name: "Alpha" }));
    await userEvent.type(screen.getByLabelText("Nom"), "!");
    await userEvent.click(screen.getByRole("button", { name: "Enregistrer" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Enregistrer" })).toBeEnabled());
    expect(screen.getByLabelText("Nom")).toHaveValue("Alpha!");
  });

  it("creates from an empty draft, then resets and hides the form", async () => {
    const { onCreate } = renderList();
    await userEvent.click(screen.getByRole("button", { name: "+ Nouveau" }));
    await userEvent.type(screen.getByLabelText("Nom"), "Charlie");
    await userEvent.click(screen.getByRole("button", { name: "Créer" }));
    expect(onCreate).toHaveBeenCalledWith("Charlie");
    await waitFor(() => expect(screen.getByRole("button", { name: "+ Nouveau" })).toBeInTheDocument());
  });

  it("removes an item only after confirmation", async () => {
    const { onRemove } = renderList();
    await userEvent.click(screen.getByRole("button", { name: "Alpha" }));
    await userEvent.click(screen.getByRole("button", { name: "Supprimer" }));
    expect(screen.getByText("Supprimer Alpha")).toBeInTheDocument();
    expect(onRemove).not.toHaveBeenCalled();
    const buttons = screen.getAllByRole("button", { name: "Supprimer" });
    await userEvent.click(buttons[buttons.length - 1]);
    expect(onRemove).toHaveBeenCalledWith("a");
  });

  it("offers neither creation nor deletion when not configured", async () => {
    renderList({ title: undefined, create: undefined, remove: undefined });
    expect(screen.queryByRole("button", { name: "+ Nouveau" })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Alpha" }));
    expect(screen.queryByRole("button", { name: "Supprimer" })).not.toBeInTheDocument();
  });
});
