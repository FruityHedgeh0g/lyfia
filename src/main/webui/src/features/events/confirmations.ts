import type { ConfirmRequest } from "../../components/molecules/ConfirmDialog/useConfirm";
import { RosterEntry } from "./registrationsApi";

type Confirm = (request: ConfirmRequest) => Promise<boolean>;

export const fullName = (entry: Pick<RosterEntry, "firstName" | "lastName">) => `${entry.firstName} ${entry.lastName}`;

/** Refuser une Demande de groupe : la personne reste inscrite sans Groupe et peut redemander. */
export const confirmRefusal = (confirm: Confirm, entry: RosterEntry) =>
  confirm({
    title: "Refuser la demande ?",
    message: `${fullName(entry)} restera inscrit(e) à l'événement, sans groupe, et pourra faire une nouvelle demande.`,
    confirmLabel: "Refuser",
  });

/** Sortir quelqu'un d'un Groupe : il reste Participant, ses passagers le suivent. */
export const confirmTakeOut = (confirm: Confirm, entry: RosterEntry) =>
  confirm({
    title: "Sortir du groupe ?",
    message: `${fullName(entry)} restera inscrit(e) à l'événement, sans groupe${entry.passagers > 0 ? ", avec ses passagers" : ""}.`,
    confirmLabel: "Sortir du groupe",
  });
