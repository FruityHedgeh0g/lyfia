import React, { useState } from "react";
import Modal from "../../components/molecules/Modal/Modal";
import FormField from "../../components/molecules/FormField/FormField";
import SearchBar from "../../components/molecules/SearchBar/SearchBar";
import Select from "../../components/atoms/Select/Select";
import Checkbox from "../../components/atoms/Checkbox/Checkbox";
import Button from "../../components/atoms/Button/Button";
import { useAllMembers } from "../../features/users/useMembers";
import { Member } from "../../features/users/types";
import { ROLE_LABELS } from "../../auth/roles";
import { Addition, ageOn, PhoneRequiredError, Poste, POSTE_LABELS, POSTES, SignUpRequirementError } from "../../features/events/registrationsApi";
import { Journee, journeeLabel } from "../../lib/journees";
import { matchesSearch } from "../../lib/textSearch";
import styles from "./AddPersonDialog.module.css";

interface AddPersonDialogProps {
  isOpen: boolean;
  onClose: () => void;
  /** Les Journées de l'Événement : toutes cochées au départ. */
  journees: Journee[];
  onAdd: (addition: Addition) => Promise<unknown>;
  pending: boolean;
  error: Error | null;
}

type NewPerson = { firstName: string; lastName: string; birthDate: string; phone: string };

const EMPTY_PERSON: NewPerson = { firstName: "", lastName: "", birthDate: "", phone: "" };

/** Au plus autant d'Inscrits proposés pendant la recherche. */
const MAX_MATCHES = 8;

/** Prénom et nom, sans casse ni accents, pour repérer un homonyme. */
const sameName = (member: Member, person: NewPerson) =>
  matchesSearch(`${member.firstName} ${member.lastName}`, `${person.firstName} ${person.lastName}`) &&
  matchesSearch(`${person.firstName} ${person.lastName}`, `${member.firstName} ${member.lastName}`);

/** Les refus de l'inscription, dits au Bureau qui inscrit quelqu'un d'autre plutôt qu'à la personne elle-même. */
const errorMessage = (error: Error) => {
  if (error instanceof PhoneRequiredError) return "Cette personne n'a pas de téléphone dans son profil : elle doit le renseigner pour être inscrite.";
  if (error instanceof SignUpRequirementError && error.code === "birth-date-required")
    return "Cette personne n'a pas encore renseigné sa date de naissance dans son profil.";
  return error.message;
};

/**
 * Le Bureau inscrit quelqu'un à un Événement à sa place (ADR 0012) : un Inscrit qui l'a appelé, ou une personne sans
 * téléphone ni internet, enregistrée au passage comme Bénévole sans compte. Mêmes règles qu'une inscription (Poste,
 * Journées, maximums, Liste d'attente) ; pour un mineur, le représentant légal a signé la décharge sur papier. Le Groupe
 * et le binôme se règlent ensuite depuis la liste.
 */
export const AddPersonDialog: React.FC<AddPersonDialogProps> = ({ isOpen, onClose, journees, onAdd, pending, error }) => {
  const { data: members } = useAllMembers();
  const [creating, setCreating] = useState(false);
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<Member | null>(null);
  const [person, setPerson] = useState<NewPerson>(EMPTY_PERSON);
  const [poste, setPoste] = useState<Poste>("conducteur");
  const [chosen, setChosen] = useState<Journee[]>(journees);
  const [signataire, setSignataire] = useState("");

  const reset = () => {
    setCreating(false);
    setSearch("");
    setSelected(null);
    setPerson(EMPTY_PERSON);
    setPoste("conducteur");
    setChosen(journees);
    setSignataire("");
  };
  const close = () => {
    reset();
    onClose();
  };

  const matches = search.trim()
    ? (members ?? []).filter((m) => matchesSearch(`${m.firstName} ${m.lastName}`, search)).slice(0, MAX_MATCHES)
    : [];
  const homonyms = creating && person.firstName.trim() && person.lastName.trim() ? (members ?? []).filter((m) => sameName(m, person)) : [];

  // Mineur à la première Journée choisie, comme le backend
  const birthDate = creating ? person.birthDate : selected?.birthDate;
  const firstJournee = [...chosen].sort()[0];
  const minor = Boolean(birthDate && firstJournee) && ageOn(birthDate!, firstJournee) < 18;

  const ready =
    (creating ? person.firstName.trim() && person.lastName.trim() && person.birthDate : selected) &&
    chosen.length > 0 &&
    (!minor || signataire.trim());

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    // La fenêtre est rendue dans un portail, mais React fait remonter l'envoi jusqu'au formulaire de l'Événement
    e.stopPropagation();
    if (!ready) return;
    const addition: Addition = {
      ...(creating
        ? { newPerson: { firstName: person.firstName.trim(), lastName: person.lastName.trim(), birthDate: person.birthDate, phone: person.phone.trim() || undefined } }
        : { personId: selected!.userId }),
      poste,
      journees: chosen.length === journees.length ? undefined : chosen,
      dechargeSignataire: minor ? signataire.trim() : undefined,
    };
    try {
      await onAdd(addition);
      close();
    } catch {
      // L'erreur est affichée sous le formulaire
    }
  };

  const toggle = (journee: Journee, on: boolean) =>
    setChosen(on ? [...chosen, journee].sort() : chosen.filter((j) => j !== journee));

  return (
    <Modal isOpen={isOpen} onClose={close} title="Ajouter une personne">
      <form className={styles.form} onSubmit={submit} noValidate>
        {!creating ? (
          <>
            {selected ? (
              <div className={styles.selected}>
                <span>
                  {selected.firstName} {selected.lastName}
                  <span className={styles.muted}> · {ROLE_LABELS[selected.role]}</span>
                </span>
                <Button type="button" label="Changer" variant="outline" onClick={() => setSelected(null)} />
              </div>
            ) : (
              <>
                <SearchBar label="Chercher un inscrit" placeholder="Prénom ou nom…" value={search} onChange={setSearch} autoFocus />
                {matches.length > 0 && (
                  <ul className={styles.matches} aria-label="Inscrits trouvés">
                    {matches.map((m) => (
                      <li key={m.userId}>
                        <button type="button" className={styles.match} onClick={() => setSelected(m)}>
                          {m.firstName} {m.lastName}
                          <span className={styles.muted}>
                            {" "}
                            · {ROLE_LABELS[m.role]}
                            {m.withoutAccount ? " · Sans compte" : ""}
                          </span>
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
                {search.trim() !== "" && matches.length === 0 && <p className={styles.muted}>Aucun inscrit ne correspond.</p>}
                <Button
                  type="button"
                  label="Personne sans compte : l'enregistrer"
                  variant="outline"
                  onClick={() => {
                    setCreating(true);
                    setSelected(null);
                  }}
                />
              </>
            )}
          </>
        ) : (
          <>
            <p className={styles.muted}>
              Pour une personne sans téléphone ni internet : le Bureau tiendra ses coordonnées à jour.{" "}
              <button type="button" className={styles.link} onClick={() => setCreating(false)}>
                Chercher plutôt un inscrit
              </button>
            </p>
            <FormField label="Prénom" value={person.firstName} onChange={(e) => setPerson({ ...person, firstName: e.target.value })} required />
            <FormField label="Nom" value={person.lastName} onChange={(e) => setPerson({ ...person, lastName: e.target.value })} required />
            {homonyms.length > 0 && (
              <div className={styles.warning} role="status">
                <span>Une personne porte déjà ce nom : est-ce elle ?</span>
                {homonyms.map((m) => (
                  <Button
                    key={m.userId}
                    type="button"
                    label={`Choisir ${m.firstName} ${m.lastName} (${ROLE_LABELS[m.role]})`}
                    variant="outline"
                    onClick={() => {
                      setCreating(false);
                      setSelected(m);
                    }}
                  />
                ))}
              </div>
            )}
            <FormField
              label="Date de naissance"
              type="date"
              value={person.birthDate}
              onChange={(e) => setPerson({ ...person, birthDate: e.target.value })}
              required
            />
            <FormField
              label="Téléphone (facultatif)"
              type="tel"
              value={person.phone}
              onChange={(e) => setPerson({ ...person, phone: e.target.value })}
            />
          </>
        )}

        <Select label="Poste" value={poste} onChange={(p) => setPoste(p as Poste)} options={POSTES.map((p) => ({ value: p, label: POSTE_LABELS[p] }))} />
        {journees.length > 1 && (
          <fieldset className={styles.journees}>
            <legend className={styles.legend}>Journées</legend>
            {journees.map((j) => (
              <Checkbox key={j} label={journeeLabel(j)} checked={chosen.includes(j)} onChange={(on) => toggle(j, on)} />
            ))}
          </fieldset>
        )}
        {minor && (
          <FormField
            label="Représentant légal ayant signé la décharge papier"
            value={signataire}
            onChange={(e) => setSignataire(e.target.value)}
            required
          />
        )}

        {error && <p className={styles.error}>{errorMessage(error)}</p>}
        <div className={styles.actions}>
          <Button type="submit" label="Inscrire" disabled={!ready || pending} />
          <Button type="button" label="Annuler" variant="outline" onClick={close} />
        </div>
      </form>
    </Modal>
  );
};

export default AddPersonDialog;
