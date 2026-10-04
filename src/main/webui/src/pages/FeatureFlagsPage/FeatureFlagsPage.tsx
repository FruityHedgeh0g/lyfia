import React from "react";
import { useFeatureFlags, useSetFeatureFlagActive } from "../../features/featureFlags/useFeatureFlags";
import { FEATURE_LABELS } from "../../features/featureFlags/types";
import Badge from "../../components/atoms/Badge/Badge";
import Button from "../../components/atoms/Button/Button";
import Spinner from "../../components/atoms/Spinner/Spinner";
import styles from "./FeatureFlagsPage.module.css";

/** Les leviers d'urgence du Super admin (ADR 0009) : une Fonctionnalité désactivée est cachée et refusée. */
export const FeatureFlagsPage: React.FC = () => {
  const { data: flags, isLoading } = useFeatureFlags();
  const setActive = useSetFeatureFlagActive();

  if (isLoading) return <Spinner label="Chargement des fonctionnalités..." />;

  return (
    <>
    {setActive.isError && (
      <p className={styles.error} role="alert">
        Le changement n'a pas pu être fait : rien n'a changé. Réessayez dans un instant.
      </p>
    )}
    <ul className={styles.list}>
      {flags?.map((flag) => (
        <li key={flag.name} className={`${styles.item}${flag.isActive ? ` ${styles.active}` : ""}`}>
          <div>
            <div className={styles.titleLine}>
              <span className={styles.title}>{FEATURE_LABELS[flag.name] ?? flag.name}</span>
              <Badge label={flag.isActive ? "Active" : "Désactivée"} tone={flag.isActive ? "accent" : "muted"} />
            </div>
            <p className={styles.description}>{flag.description}</p>
          </div>
          <Button
            label={flag.isActive ? "Désactiver" : "Activer"}
            variant={flag.isActive ? "outline" : "accent"}
            disabled={setActive.isPending}
            onClick={() => setActive.mutate({ name: flag.name, isActive: !flag.isActive })}
          />
        </li>
      ))}
    </ul>
    </>
  );
};

export default FeatureFlagsPage;
