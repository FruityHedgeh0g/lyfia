import React, { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useEvent } from "../../features/events/useEvents";
import { acceptsSignUps, EVENT_STATUS_LABELS, statusTone } from "../../features/events/status";
import { useEventRegistration, useMyRegistrations, usePilotes } from "../../features/events/useMyRegistrations";
import { DEMANDE_STATUS_LABELS, passagerLabel, PhoneRequiredError, Registration, RideMode } from "../../features/events/registrationsApi";
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
import FormField from "../../components/molecules/FormField/FormField";
import ButtonLink from "../../components/atoms/ButtonLink/ButtonLink";
import { useSectors } from "../../features/sectors/useSector";
import Icon from "../../components/atoms/Icon/Icon";
import FeatureNotice from "../../components/molecules/FeatureNotice/FeatureNotice";
import styles from "./EventDetailPage.module.css";

/** Ce que la personne est à cet Événement : pilote, ou passager de son pilote. */
const registeredAs = ({ status, pilote }: Registration) => {
  if (status === "en_attente") return pilote ? `Vous êtes sur la liste d'attente (${passagerLabel(pilote)})` : "Vous êtes sur la liste d'attente";
  return pilote ? `Vous êtes inscrit comme passager de ${pilote.firstName} ${pilote.lastName}` : "Vous êtes inscrit comme pilote";
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
  const [mode, setMode] = useState<RideMode>("pilote");
  const [piloteId, setPiloteId] = useState("");
  const { data: pilotes } = usePilotes(mode === "passager" ? eventId : undefined);
  const registrationOpen = useFeature("inscription-evenements", event?.sectorId);
  const registrationOff = useFeatureOff("inscription-evenements", event?.sectorId);
  const [askPhone, setAskPhone] = useState(false);
  const [phone, setPhone] = useState("");

  const registration = registrations?.find((r) => r.eventId === eventId);
  const isPending = register.isPending || unregister.isPending || askGroup.isPending;

  /** Sans téléphone, l'API refuse l'inscription : on le demande, on l'enregistre au profil, puis on réessaie. */
  const signUp = (withPhone?: string) => {
    if (!eventId) return;
    register.mutate(
      mode === "passager" ? { eventId, phone: withPhone, piloteId } : { eventId, phone: withPhone, groupId },
      {
        onSuccess: () => setAskPhone(false),
        onError: (error) => setAskPhone(error instanceof PhoneRequiredError),
      }
    );
  };

  const savePhoneAndSignUp = (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || !phone.trim()) return;
    const withPhone = phone.trim();
    // Inscrit même si le profil n'a pas pu être mis à jour : le téléphone accompagne l'inscription
    updateProfile({ phone: withPhone }).finally(() => signUp(withPhone));
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
  /** À partir de Membre, on ne roule qu'aux Événements de son Secteur (ADR 0004). */
  const reservedToItsSecteur = Boolean(user?.sector) && user?.sector?.sectorId !== event.sectorId;
  /** Les Groupes du Secteur de l'Événement, pour la Demande de groupe. */
  const groupOptions = [
    { value: "", label: "Sans groupe" },
    ...(groups ?? []).filter((g) => g.sectorId === event.sectorId).map((g) => ({ value: g.groupId, label: g.name })),
  ];
  const groupSelect = <Select label="Groupe (facultatif)" value={groupId} onChange={setGroupId} options={groupOptions} />;
  const canAskGroup =
    registration?.mode === "pilote" && !registration.group && registration.demande?.status !== "en_attente" && event.status !== "archive";
  /** Un passager choisit un pilote déjà inscrit ; il roulera avec son Groupe. */
  const piloteOptions = [
    { value: "", label: "Choisissez votre pilote" },
    ...(pilotes ?? []).filter((p) => p.userId !== user?.userId).map((p) => ({ value: p.userId, label: `${p.firstName} ${p.lastName}` })),
  ];
  const signUpLabel =
    event.status === "complet" ? "Rejoindre la liste d'attente" : mode === "passager" ? "M'inscrire comme passager" : "M'inscrire comme pilote";

  return (
    <article>
      <PageHero eyebrow="Événement" title={event.name} imageSrc={event.imageUrl || undefined}>
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
              src={event.imageUrl || placeholderImage(event.eventId, event.name)}
              alt={event.name}
            />
            <p className={styles.description}>{event.description}</p>
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
                {registration.group && <p>Vous roulez avec {registration.group.name}.</p>}
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
                    {groupSelect}
                    <Button
                      label="Demander ce groupe"
                      variant="outline"
                      disabled={isPending || !groupId}
                      onClick={() => askGroup.mutate({ eventId: event.eventId, groupId })}
                    />
                  </>
                )}
                {event.status !== "archive" && (
                  <Button
                    label="Me désinscrire"
                    variant="outline"
                    disabled={isPending}
                    onClick={() => unregister.mutate(event.eventId)}
                  />
                )}
              </div>
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
                  ) : askPhone ? (
                    <form onSubmit={savePhoneAndSignUp} noValidate>
                      <FormField
                        label="Téléphone"
                        type="tel"
                        autoComplete="tel"
                        value={phone}
                        onChange={(e) => setPhone(e.target.value)}
                        required
                      />
                      <p>Votre numéro est nécessaire pour vous inscrire ; il est enregistré dans votre profil.</p>
                      <Button type="submit" label="Enregistrer et m'inscrire" variant="accent" disabled={isPending} />
                    </form>
                  ) : (
                    <>
                      <Select
                        label="Je roule comme"
                        value={mode}
                        onChange={(value) => setMode(value as RideMode)}
                        options={[
                          { value: "pilote", label: "Pilote" },
                          { value: "passager", label: "Passager" },
                        ]}
                      />
                      {mode === "pilote" ? (
                        groupSelect
                      ) : (
                        <Select label="Pilote" value={piloteId} onChange={setPiloteId} options={piloteOptions} />
                      )}
                      <Button
                        label={signUpLabel}
                        variant="accent"
                        disabled={isPending || (mode === "passager" && !piloteId)}
                        onClick={() => signUp()}
                      />
                      {register.isError && !askPhone && <p className={styles.error}>{register.error.message}</p>}
                    </>
                  )}
                </div>
              )
            )}
          </aside>
        </div>
      </div>
    </article>
  );
};

export default EventDetailPage;
