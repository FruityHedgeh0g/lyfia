import type { ConfirmRequest } from "../../components/molecules/ConfirmDialog/useConfirm";
import { PersonRef, RosterEntry } from "./registrationsApi";

type Confirm = (request: ConfirmRequest) => Promise<boolean>;

export const fullName = (entry: Pick<RosterEntry, "firstName" | "lastName">) => `${entry.firstName} ${entry.lastName}`;

/** Refuser une Demande de groupe : la personne reste inscrite sans Groupe et peut redemander. */
export const confirmRefusal = (confirm: Confirm, entry: RosterEntry) =>
  confirm({
    title: "Refuser la demande ?",
    message: `${fullName(entry)} restera inscrit(e) à l'événement, sans groupe, et pourra faire une nouvelle demande.`,
    confirmLabel: "Refuser",
  });

/** Sortir quelqu'un d'un Groupe : il reste Participant. */
export const confirmTakeOut = (confirm: Confirm, entry: RosterEntry) =>
  confirm({
    title: "Sortir du groupe ?",
    message: `${fullName(entry)} restera inscrit(e) à l'événement, sans groupe.`,
    confirmLabel: "Sortir du groupe",
  });

/**
 * Celui qui retire, sort ou déplace un conducteur choisit si son passager suit (ADR 0011) ; sans passager, rien à
 * demander. Laissé, le passager reste inscrit, sans conducteur, signalé pour être associé à un autre.
 */
export const askWithPassager = async (confirm: Confirm, conducteur: string, passager: PersonRef | null, what: string) =>
  !passager ||
  confirm({
    title: `Et ${fullName(passager)} ?`,
    message: `${fullName(passager)} roule derrière ${conducteur}. ${what} Sinon, ${fullName(passager)} reste inscrit(e), sans conducteur, et pourra être associé(e) à un autre.`,
    confirmLabel: "Avec son passager",
    cancelLabel: "Sans son passager",
  });
