import React, { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useEvent } from "../../features/events/useEvents";
import { acceptsSignUps, EVENT_STATUS_LABELS, statusTone } from "../../features/events/status";
import { useEventRegistration, useMyRegistrations, useConducteurs } from "../../features/events/useMyRegistrations";
import { DEMANDE_STATUS_LABELS, groupKindOf, passagerLabel, PhoneRequiredError, Poste, POSTE_LABELS, POSTES, Registration } from "../../features/events/registrationsApi";
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
import styles from "./EventDetailPage.module.css";

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
  const [conducteurId, setConducteurId] = useState("");
  const { data: conducteurs } = useConducteurs(poste === "passager" ? eventId : undefined);
  const registrationOpen = useFeature("inscription-evenements", event?.sectorId);
  const registrationOff = useFeatureOff("inscription-evenements", event?.sectorId);
  const [askPhone, setAskPhone] = useState(false);
  const [phone, setPhone] = useState("");

  const registration = registrations?.find((r) => r.eventId === eventId);
  const { confirm, dialog } = useConfirm();
  const isPending = register.isPending || unregister.isPending || askGroup.isPending;

  /** Sans téléphone, l'API refuse l'inscription : on le demande, on l'enregistre au profil, puis on réessaie. */
  const signUp = (withPhone?: string) => {
    if (!eventId) return;
    register.mutate(
      poste === "passager"
        ? { eventId, poste, phone: withPhone, conducteurId }
        : { eventId, poste, phone: withPhone, groupId: groupKindOf(poste) ? groupId : undefined },
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
    registration.poste !== "passager" &&
    groupKindOf(registration.poste) !== null &&
    !registration.group &&
    registration.demande?.status !== "en_attente" &&
    event.status !== "archive";
  /** Un passager choisit un conducteur déjà inscrit ; il roulera avec son Groupe. */
  const conducteurOptions = [
    { value: "", label: "Choisissez votre conducteur" },
    ...(conducteurs ?? []).filter((p) => p.userId !== user?.userId).map((p) => ({ value: p.userId, label: `${p.firstName} ${p.lastName}` })),
  ];
  const signUpLabel =
    event.status === "complet" ? "Rejoindre la liste d'attente" : `M'inscrire comme ${POSTE_LABELS[poste].toLowerCase()}`;

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
                    onClick={async () => {
                      const confirmed = await confirm({
                        title: "Se désinscrire ?",
                        message:
                          registration.poste === "conducteur"
                            ? `Votre place à « ${event.name} » sera libérée, et vos éventuels passagers seront désinscrits avec vous.`
                            : `Votre place à « ${event.name} » sera libérée.`,
                        confirmLabel: "Me désinscrire",
                      });
                      if (confirmed) unregister.mutate(event.eventId);
                    }}
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
                        label="Poste"
                        value={poste}
                        onChange={(value) => {
                          setPoste(value as Poste);
                          setGroupId("");
                        }}
                        options={POSTES.map((p) => ({ value: p, label: POSTE_LABELS[p] }))}
                      />
                      {poste === "passager" ? (
                        <Select label="Conducteur" value={conducteurId} onChange={setConducteurId} options={conducteurOptions} />
                      ) : (
                        groupKindOf(poste) && groupSelect(poste)
                      )}
                      <Button
                        label={signUpLabel}
                        variant="accent"
                        disabled={isPending || (poste === "passager" && !conducteurId)}
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
      {dialog}
    </article>
  );
};

export default EventDetailPage;
