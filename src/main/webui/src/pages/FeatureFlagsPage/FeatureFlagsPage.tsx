import React, { useState } from "react";
import { Link } from "react-router-dom";
import { useFeatureFlags, useSetFeatureFlagActive, useSetFeatureFlagActiveInSector } from "../../features/featureFlags/useFeatureFlags";
import { useSectors } from "../../features/sectors/useSector";
import { Sector } from "../../features/sectors/types";
import { FEATURE_LABELS, FeatureFlag } from "../../features/featureFlags/types";
import { entry } from "../../auth/access";
import { formatDate, formatTime } from "../../lib/formatDate";
import Badge from "../../components/atoms/Badge/Badge";
import Button from "../../components/atoms/Button/Button";
import Spinner from "../../components/atoms/Spinner/Spinner";
import FormField from "../../components/molecules/FormField/FormField";
import styles from "./FeatureFlagsPage.module.css";

/** Qui a changé le levier en dernier, quand et pourquoi (le Journal). */
const lastSwitch = (flag: FeatureFlag) => {
  if (!flag.lastSwitchedAt) return null;
  const who = flag.lastSwitchedBy ?? "quelqu'un";
  const when = `le ${formatDate(flag.lastSwitchedAt)} à ${formatTime(flag.lastSwitchedAt)}`;
  return `${flag.isActive ? "Activée" : "Désactivée"} par ${who} ${when}${flag.lastReason ? ` — « ${flag.lastReason} »` : ""}`;
};

interface FeatureLeverProps {
  flag: FeatureFlag;
  pending: boolean;
  onSwitch: (reason: string) => void;
  /** Les Secteurs ouverts, pour une Fonctionnalité qui a aussi un levier par Secteur. */
  sectors: Sector[];
  onSwitchSector: (sectorId: string, isActive: boolean, reason: string) => void;
}

const FeatureLever: React.FC<FeatureLeverProps> = ({ flag, pending, onSwitch, sectors, onSwitchSector }) => {
  const [reason, setReason] = useState("");
  const label = FEATURE_LABELS[flag.name] ?? flag.name;
  const switched = lastSwitch(flag);

  return (
    <li className={`${styles.item}${flag.isActive ? ` ${styles.active}` : ""}`}>
      <div className={styles.body}>
        <div className={styles.titleLine}>
          <span className={styles.title}>{label}</span>
          <Badge label={flag.isActive ? "Active" : "Désactivée"} tone={flag.isActive ? "accent" : "muted"} />
        </div>
        <p className={styles.description}>{flag.description}</p>
        {switched && <p className={styles.meta}>{switched}</p>}
        {!flag.isActive && (
          <p className={styles.meta}>
            {flag.refusedCount ?? 0} tentative{(flag.refusedCount ?? 0) > 1 ? "s" : ""} refusée{(flag.refusedCount ?? 0) > 1 ? "s" : ""}{" "}
            depuis la coupure
          </p>
        )}
      </div>
      <form
        className={styles.lever}
        onSubmit={(e) => {
          e.preventDefault();
          onSwitch(reason);
          setReason("");
        }}
      >
        <FormField label={`Raison (facultatif) — ${label}`} value={reason} onChange={(e) => setReason(e.target.value)} />
        <Button
          type="submit"
          label={flag.isActive ? "Désactiver" : "Activer"}
          variant={flag.isActive ? "outline" : "accent"}
          disabled={pending}
        />
      </form>
      {flag.perSecteur && sectors.length > 0 && (
        <ul className={styles.sectors} aria-label={`${label} par secteur`}>
          {sectors.map((sector) => {
            const sectorOn = !flag.offSectors?.includes(sector.sectorId);
            return (
              <li key={sector.sectorId} className={styles.sector}>
                <span>
                  {sector.name} : {sectorOn ? "active" : "désactivée"}
                  {sectorOn && !flag.isActive ? " (mais désactivée pour tout le site)" : ""}
                </span>
                <Button
                  label={sectorOn ? `Désactiver pour ${sector.name}` : `Activer pour ${sector.name}`}
                  variant="outline"
                  disabled={pending}
                  onClick={() => {
                    onSwitchSector(sector.sectorId, !sectorOn, reason);
                    setReason("");
                  }}
                />
              </li>
            );
          })}
        </ul>
      )}
    </li>
  );
};

/** Les leviers d'urgence du Super admin (ADR 0009) : une Fonctionnalité désactivée est cachée et refusée. */
export const FeatureFlagsPage: React.FC = () => {
  const { data: flags, isLoading } = useFeatureFlags();
  const setActive = useSetFeatureFlagActive();
  const setActiveInSector = useSetFeatureFlagActiveInSector();
  // Un Secteur fermé est en lecture seule (ADR 0003)
  const openSectors = (useSectors().data ?? []).filter((s) => !s.closed);
  const failed = setActive.isError || setActiveInSector.isError;

  if (isLoading) return <Spinner label="Chargement des fonctionnalités..." />;

  return (
    <>
      <p className={styles.intro}>
        Chaque changement est inscrit au <Link to={entry("adminJournal").path}>Journal</Link>.
      </p>
      {failed && (
        <p className={styles.error} role="alert">
          Le changement n'a pas pu être fait : rien n'a changé. Réessayez dans un instant.
        </p>
      )}
      <ul className={styles.list}>
        {flags?.map((flag) => (
          <FeatureLever
            key={flag.name}
            flag={flag}
            pending={setActive.isPending || setActiveInSector.isPending}
            onSwitch={(reason) => setActive.mutate({ name: flag.name, isActive: !flag.isActive, reason })}
            sectors={openSectors}
            onSwitchSector={(sectorId, isActive, reason) => setActiveInSector.mutate({ name: flag.name, sectorId, isActive, reason })}
          />
        ))}
      </ul>
    </>
  );
};

export default FeatureFlagsPage;
