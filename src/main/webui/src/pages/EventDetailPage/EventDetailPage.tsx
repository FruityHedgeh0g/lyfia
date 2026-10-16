import React, { useState } from "react";
import EventImages from "./EventImages";
import { eventImageSrc } from "../../features/events/types";
import { Link, useParams } from "react-router-dom";
import { useEvent } from "../../features/events/useEvents";
import { acceptsSignUps, EVENT_STATUS_LABELS, statusTone } from "../../features/events/status";
import { useEventRegistration, useMyRegistrations } from "../../features/events/useMyRegistrations";
import { DEMANDE_STATUS_LABELS, groupKindOf, passagerLabel, PhoneRequiredError, Poste, SignUpRequirementError, POSTE_LABELS, POSTES, Registration } from "../../features/events/registrationsApi";
import { useGroups } from "../../features/groups/useGroups";
import Select from "../../components/atoms/Select/Select";
import { useFeature, useFeatureOff } from "../../features/featureFlags/useFeatureFlags";
import { useAuth } from "../../auth/AuthContext";
import { formatDateRange } from "../../lib/formatDate";
import { placeholderImage } from "../../lib/placeholderImage";
import PageHero from "../../components/organisms/PageHero/PageHero";
import Spinner from "../../components/atoms/Spinner/Spinner";
import Badge from "../../components/atoms/Badge/Badge";
import Button from "../../components/atoms/Button/Button";
import { useConfirm } from "../../components/molecules/ConfirmDialog/useConfirm";
import FormField from "../../components/molecules/FormField/FormField";
import ButtonLink from "../../components/atoms/ButtonLink/ButtonLink";
import { useSectors } from "../../features/sectors/useSector";
import Icon from "../../components/atoms/Icon/Icon";
import FeatureNotice from "../../components/molecules/FeatureNotice/FeatureNotice";
import Checkbox from "../../components/atoms/Checkbox/Checkbox";
import DechargeModal from "../../features/decharge/DechargeModal";
import { askWithPassager } from "../../features/events/confirmations";
import { eventJournees, Journee, journeeLabel } from "../../lib/journees";
import styles from "./EventDetailPage.module.css";

/** « Samedi 9 mai ». */
const capitalized = (journee: Journee) => {
  const label = journeeLabel(journee);
  return label.charAt(0).toUpperCase() + label.slice(1);
};

/** Ce que la personne est à cet Événement : son Poste, ou passager de son conducteur. */
const registeredAs = ({ status, poste, conducteur }: Registration) => {
  if (status === "en_attente") return conducteur ? `Vous êtes sur la liste d'attente (${passagerLabel(conducteur)})` : "Vous êtes sur la liste d'attente";
  return conducteur
    ? `Vous êtes inscrit comme passager de ${conducteur.firstName} ${conducteur.lastName}`
    : `Vous êtes inscrit comme ${POSTE_LABELS[poste].toLowerCase()}`;
};

export const EventDetailPage: React.FC = () => {
  const { eventId } = useParams<{ eventId: string }>();
  const { data: event, isLoading, isError } = useEvent(eventId);
  const { isAuthenticated, user, updateProfile } = useAuth();
  const { data: sectors } = useSectors();
  const { data: registrations } = useMyRegistrations();
  const { register, unregister, askGroup } = useEventRegistration();
  const { data: groups } = useGroups();
  const [groupId, setGroupId] = useState("");
  const [poste, setPoste] = useState<Poste>("conducteur");
  /** Le prénom et le nom du conducteur avec qui l'on vient, tapés librement : la liste des inscrits n'est jamais montrée. */
  const [conducteur, setConducteur] = useState("");
  /** Les Journées choisies ; null : tout l'Événement (ADR 0011). */
  const [chosen, setChosen] = useState<Journee[] | null>(null);
  /** Les Journées à ajouter à son inscription. */
  const [added, setAdded] = useState<Journee[]>([]);
  const registrationOpen = useFeature("inscription-evenements", event?.sectorId);
  const registrationOff = useFeatureOff("inscription-evenements", event?.sectorId);
  /** Ce que l'API demande encore avant d'inscrire : le téléphone et la date de naissance manquants au profil. */
  const [askProfile, setAskProfile] = useState(false);
  const [phone, setPhone] = useState("");
  const [birthDate, setBirthDate] = useState("");
  /** Pour un mineur : la décharge que son représentant légal approuve. */
  const [askDecharge, setAskDecharge] = useState(false);

  const registration = registrations?.find((r) => r.eventId === eventId);
  const { confirm, dialog } = useConfirm();
  const isPending = register.isPending || unregister.isPending || askGroup.isPending;

  /**
   * Sans téléphone ni date de naissance, l'API refuse l'inscription : on les demande, on les enregistre au profil,
   * puis on réessaie. Pour un mineur, son représentant légal approuve la décharge, puis on réessaie avec son nom.
   */
  const signUp = (signataire?: string) => {
    if (!eventId) return;
    const journees = chosen ?? undefined;
    register.mutate(
      poste === "passager" && conducteur.trim()
        ? { eventId, poste, conducteur, journees, signataire }
        : { eventId, poste, groupId: groupKindOf(poste) ? groupId : undefined, journees, signataire },
      {
        onSuccess: () => {
          setAskProfile(false);
          setAskDecharge(false);
        },
        onError: (error) => {
          setAskProfile(
            error instanceof PhoneRequiredError || (error instanceof SignUpRequirementError && error.code === "birth-date-required")
          );
          setAskDecharge(error instanceof SignUpRequirementError && error.code === "decharge-requise");
        },
      }
    );
  };

  const saveProfileAndSignUp = (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || (!user.phone && !phone.trim()) || (!user.birthDate && !birthDate)) return;
    updateProfile({ phone: user.phone || phone.trim(), birthDate: user.birthDate || birthDate }).then(
      () => signUp(),
      () => undefined
    );
  };

  const back = (
    <Link className={styles.back} to="/evenements">
      <Icon name="arrowLeft" size={16} strokeWidth={2.5} /> Retour aux événements
    </Link>
  );

  if (!event) {
    return (
      <div className="container">
        {back}
        {isLoading && <Spinner label="Chargement de l'événement..." />}
        {isError && <p className={styles.error}>Impossible de charger cet événement.</p>}
        {!isLoading && !isError && <p className={styles.error}>Cet événement n'existe pas.</p>}
      </div>
    );
  }

  const signUpsOpen = acceptsSignUps(event.status);
  const allJournees = eventJournees(event.startDateTime, event.endDateTime);
  const picked = chosen ?? allJournees;
  /** Cocher toutes les Journées revient à s'inscrire à tout l'Événement. */
  const pick = (journee: Journee, on: boolean) => {
    const next = allJournees.filter((j) => (j === journee ? on : picked.includes(j)));
    setChosen(next.length === allJournees.length ? null : next);
  };
  const missing = registration ? allJournees.filter((j) => !registration.journees.some((r) => r.journee === j)) : [];

  /**
   * Se retirer de Journées (aucune : de tout l'Événement). Qui a un passager ces Journées-là choisit s'il se retire
   * aussi ; laissé, il reste inscrit, sans conducteur.
   */
  const leave = async (journees?: Journee[]) => {
    if (!registration) return;
    const confirmed = await confirm({
      title: journees ? `Vous retirer du ${journeeLabel(journees[0])} ?` : "Se désinscrire ?",
      message: journees ? "Votre place ce jour-là sera libérée." : `Votre place à « ${event.name} » sera libérée.`,
      confirmLabel: journees ? "Me retirer" : "Me désinscrire",
    });
    if (!confirmed) return;
    const passager = registration.journees.find((j) => j.passager && (!journees || journees.includes(j.journee)))?.passager ?? null;
    const withPassager = await askWithPassager(confirm, "vous", passager, "Le désinscrire aussi ?");
    unregister.mutate({ eventId: event.eventId, journees, withPassager });
  };
  /** À partir de Membre, on ne roule qu'aux Événements de son Secteur (ADR 0004). */
  const reservedToItsSecteur = Boolean(user?.sector) && user?.sector?.sectorId !== event.sectorId;
  /** Les Groupes du Secteur de l'Événement de la sorte qui va avec le Poste, pour la Demande de groupe. */
  const groupSelect = (forPoste: Poste) => (
    <Select
      label="Groupe (facultatif)"
      value={groupId}
      onChange={setGroupId}
      options={[
        { value: "", label: "Sans groupe" },
        ...(groups ?? [])
          .filter((g) => g.sectorId === event.sectorId && g.kind === groupKindOf(forPoste))
          .map((g) => ({ value: g.groupId, label: g.name })),
      ]}
    />
  );
  const canAskGroup =
    registration !== undefined &&
    groupKindOf(registration.poste) !== null &&
    !registration.group &&
    registration.demande?.status !== "en_attente" &&
    event.status !== "archive";
  const signUpLabel =
    event.status === "complet" ? "Rejoindre la liste d'attente" : `M'inscrire comme ${POSTE_LABELS[poste].toLowerCase()}`;

  return (
    <article>
      <PageHero eyebrow="Événement" title={event.name} imageSrc={eventImageSrc(event, undefined)}>
        <Badge label={EVENT_STATUS_LABELS[event.status]} tone={statusTone(event.status)} />
        <span className={styles.heroMeta}>
          <Icon name="calendar" size={18} />
          {formatDateRange(event.startDateTime, event.endDateTime)}
        </span>
      </PageHero>

      <div className="container">
        {back}
        <div className={styles.layout}>
          <div>
            <img
              className={styles.image}
              src={eventImageSrc(event, placeholderImage(event.eventId, event.name))}
              alt={event.name}
            />
            <p className={styles.description}>{event.description}</p>
            <EventImages event={event} />
          </div>

          <aside className={styles.card}>
            <p className="eyebrow">Infos pratiques</p>
            <ul className={styles.facts}>
              <li>
                <Icon name="calendar" size={20} />
                <span>{formatDateRange(event.startDateTime, event.endDateTime)}</span>
              </li>
              {event.address && (
                <li>
                  <Icon name="pin" size={20} />
                  <span>
                    {event.address}, {event.postalCode} {event.city}
                  </span>
                </li>
              )}
            </ul>

            {registration ? (
              <div className={styles.actions}>
                <p className={styles.registered}>
                  <Icon name="check" size={18} strokeWidth={3} />
                  {registeredAs(registration)}
                </p>
                {allJournees.length > 1 ? (
                  <ul className={styles.journees} aria-label="Mes journées">
                    {registration.journees.map((j) => (
                      <li key={j.journee}>
                        <span>
                          {capitalized(j.journee)} : {j.status === "participant" ? "participant" : "liste d'attente"}
                          {j.conducteur ? ` · derrière ${j.conducteur.firstName} ${j.conducteur.lastName}` : registration.poste === "passager" ? " · sans conducteur" : ""}
                          {j.passager && ` · votre passager : ${j.passager.firstName} ${j.passager.lastName}`}
                        </span>
                        {registration.journees.length > 1 && event.status !== "archive" && !registration.leadsGroup && (
                          <Button label="Me retirer" variant="outline" disabled={isPending} onClick={() => leave([j.journee])} />
                        )}
                      </li>
                    ))}
                  </ul>
                ) : (
                  registration.journees[0]?.passager && (
                    <p>
                      Votre passager : {registration.journees[0].passager.firstName} {registration.journees[0].passager.lastName}
                    </p>
                  )
                )}
                {missing.length > 0 && signUpsOpen && registrationOpen && !registration.leadsGroup && (
                  <fieldset className={styles.journeesPicker}>
                    <legend>Ajouter des journées</legend>
                    {missing.map((j) => (
                      <Checkbox
                        key={j}
                        label={capitalized(j)}
                        checked={added.includes(j)}
                        onChange={(on) => setAdded(on ? [...added, j] : added.filter((a) => a !== j))}
                      />
                    ))}
                    <Button
                      label="Ajouter ces journées"
                      variant="outline"
                      disabled={isPending || added.length === 0}
                      onClick={() =>
                        register.mutate(
                          { eventId: event.eventId, poste: registration.poste, journees: added },
                          { onSuccess: () => setAdded([]) }
                        )
                      }
                    />
                  </fieldset>
                )}
                {registration.group &&
                  (registration.leadsGroup ? (
                    <p>Vous menez {registration.group.name} : votre place vous est réservée tant que vous menez ce groupe.</p>
                  ) : (
                    <p>Vous roulez avec {registration.group.name}.</p>
                  ))}
                {registration.demande && registration.demande.status !== "acceptee" && (
                  <p>
                    Demande pour {registration.demande.group.name} : {DEMANDE_STATUS_LABELS[registration.demande.status]}
                  </p>
                )}
                {canAskGroup && registrationOff && (
                  <FeatureNotice feature="inscription-evenements" message="Les demandes de groupe sont temporairement suspendues." />
                )}
                {canAskGroup && registrationOpen && (
                  <>
                    {groupSelect(registration.poste)}
                    <Button
                      label="Demander ce groupe"
                      variant="outline"
                      disabled={isPending || !groupId}
                      onClick={() => askGroup.mutate({ eventId: event.eventId, groupId })}
                    />
                  </>
                )}
                {event.status !== "archive" && !registration.leadsGroup && (
                  <Button
                    label="Me désinscrire"
                    variant="outline"
                    disabled={isPending}
                    onClick={() => leave()}
                  />
                )}
              </div>
            ) : user?.role === "super_admin" ? (
              <p>Le compte Super admin ne s'inscrit pas aux événements : on y roule avec son compte personnel.</p>
            ) : reservedToItsSecteur ? (
              <p>Réservé aux membres de {sectors?.find((s) => s.sectorId === event.sectorId)?.name ?? "son secteur"} et aux bénévoles.</p>
            ) : signUpsOpen && registrationOff ? (
              <FeatureNotice feature="inscription-evenements" message="Les inscriptions en ligne sont temporairement suspendues." />
            ) : (
              signUpsOpen &&
              registrationOpen && (
                <div className={styles.actions}>
                  {!isAuthenticated ? (
                    <ButtonLink to="/connexion" label="Se connecter pour m'inscrire" />
                  ) : askProfile ? (
                    <form onSubmit={saveProfileAndSignUp} noValidate>
                      {!user?.phone && (
                        <FormField
                          label="Téléphone"
                          type="tel"
                          autoComplete="tel"
                          value={phone}
                          onChange={(e) => setPhone(e.target.value)}
                          required
                        />
                      )}
                      {!user?.birthDate && (
                        <FormField
                          label="Date de naissance"
                          type="date"
                          autoComplete="bday"
                          value={birthDate}
                          max={new Date().toISOString().slice(0, 10)}
                          onChange={(e) => setBirthDate(e.target.value)}
                          required
                        />
                      )}
                      <p>Ces informations sont nécessaires pour vous inscrire ; elles sont enregistrées dans votre profil.</p>
                      <Button type="submit" label="Enregistrer et m'inscrire" variant="accent" disabled={isPending} />
                    </form>
                  ) : (
                    <>
                      <Select
                        label="Poste"
                        value={poste}
                        onChange={(value) => {
                          setPoste(value as Poste);
                          setGroupId("");
                        }}
                        options={POSTES.map((p) => ({ value: p, label: POSTE_LABELS[p] }))}
                      />
                      {allJournees.length > 1 && (
                        <fieldset className={styles.journeesPicker}>
                          <legend>Journées</legend>
                          <Checkbox label="Tout l'événement" checked={chosen === null} onChange={(all) => setChosen(all ? null : [])} />
                          {allJournees.map((j) => (
                            <Checkbox key={j} label={capitalized(j)} checked={picked.includes(j)} onChange={(on) => pick(j, on)} />
                          ))}
                        </fieldset>
                      )}
                      {/* « Je viens avec » : l'API rapproche le nom tapé des conducteurs inscrits ; sans nom, le Bureau ou le Chef l'associera */}
                      {poste === "passager" && (
                        <FormField
                          label="Prénom et nom de votre conducteur (facultatif)"
                          value={conducteur}
                          autoComplete="off"
                          onChange={(e) => setConducteur(e.target.value)}
                        />
                      )}
                      {groupKindOf(poste) && !(poste === "passager" && conducteur.trim()) && groupSelect(poste)}
                      <Button
                        label={signUpLabel}
                        variant="accent"
                        disabled={isPending || picked.length === 0}
                        onClick={() => signUp()}
                      />
                      {register.isError && !askProfile && !askDecharge && <p className={styles.error}>{register.error.message}</p>}
                    </>
                  )}
                </div>
              )
            )}
          </aside>
        </div>
      </div>
      {dialog}
      <DechargeModal isOpen={askDecharge} onClose={() => setAskDecharge(false)} onApprove={(signataire) => signUp(signataire)} pending={isPending} />
    </article>
  );
};

export default EventDetailPage;
