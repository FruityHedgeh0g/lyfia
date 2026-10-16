import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

/** Confirme dans la boîte de confirmation ouverte (useConfirm), la dernière ouverte s'il y en a d'autres dessous. */
export async function confirmIn(dialogAction: string) {
  const dialogs = await screen.findAllByRole("dialog");
  const dialog = dialogs[dialogs.length - 1];
  await userEvent.click(within(dialog).getByRole("button", { name: dialogAction }));
}
