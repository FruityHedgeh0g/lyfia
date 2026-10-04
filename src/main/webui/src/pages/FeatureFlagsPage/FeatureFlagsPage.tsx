import React, { useState } from "react";
import { Link } from "react-router-dom";
import { useFeatureFlags, useSetFeatureFlagActive } from "../../features/featureFlags/useFeatureFlags";
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

const FeatureLever: React.FC<{ flag: FeatureFlag; pending: boolean; onSwitch: (reason: string) => void }> = ({
  flag,
  pending,
  onSwitch,
}) => {
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
    </li>
  );
};

/** Les leviers d'urgence du Super admin (ADR 0009) : une Fonctionnalité désactivée est cachée et refusée. */
export const FeatureFlagsPage: React.FC = () => {
  const { data: flags, isLoading } = useFeatureFlags();
  const setActive = useSetFeatureFlagActive();

  if (isLoading) return <Spinner label="Chargement des fonctionnalités..." />;

  return (
    <>
      <p className={styles.intro}>
        Chaque changement est inscrit au <Link to={entry("adminJournal").path}>Journal</Link>.
      </p>
      {setActive.isError && (
        <p className={styles.error} role="alert">
          Le changement n'a pas pu être fait : rien n'a changé. Réessayez dans un instant.
        </p>
      )}
      <ul className={styles.list}>
        {flags?.map((flag) => (
          <FeatureLever
            key={flag.name}
            flag={flag}
            pending={setActive.isPending}
            onSwitch={(reason) => setActive.mutate({ name: flag.name, isActive: !flag.isActive, reason })}
          />
        ))}
      </ul>
    </>
  );
};

export default FeatureFlagsPage;
