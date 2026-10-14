import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

/** Confirme dans la boîte de confirmation ouverte (useConfirm), par le bouton de l'action. */
export async function confirmIn(dialogAction: string) {
  const dialog = await screen.findByRole("dialog");
  await userEvent.click(within(dialog).getByRole("button", { name: dialogAction }));
}
