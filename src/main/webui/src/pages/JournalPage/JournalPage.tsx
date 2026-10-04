import React, { useEffect, useRef } from "react";
import { useJournal } from "../../features/featureFlags/useFeatureFlags";
import { FEATURE_LABELS, JournalEntry } from "../../features/featureFlags/types";
import { formatDate, formatTime } from "../../lib/formatDate";
import Badge from "../../components/atoms/Badge/Badge";
import Button from "../../components/atoms/Button/Button";
import Spinner from "../../components/atoms/Spinner/Spinner";
import styles from "./JournalPage.module.css";

const Entry: React.FC<{ entry: JournalEntry }> = ({ entry }) => (
  <li className={styles.entry}>
    <div className={styles.line}>
      <span className={styles.feature}>{FEATURE_LABELS[entry.feature] ?? entry.feature}</span>
      <Badge label={entry.isActive ? "Activée" : "Désactivée"} tone={entry.isActive ? "accent" : "muted"} />
      <span className={styles.scope}>{entry.sectorName ? `Secteur ${entry.sectorName}` : "Tout le site"}</span>
    </div>
    <p className={styles.meta}>
      {entry.switchedBy ?? "Quelqu'un"}, le {formatDate(entry.switchedAt)} à {formatTime(entry.switchedAt)}
    </p>
    {entry.reason && <p className={styles.reason}>« {entry.reason} »</p>}
  </li>
);

/**
 * Le Journal du Super admin (ADR 0009) : chaque fois qu'une Fonctionnalité a été désactivée ou réactivée, les plus
 * récentes d'abord. Chargé page après page en défilant, pour ne jamais tout charger d'un coup.
 */
export const JournalPage: React.FC = () => {
  const { data, isLoading, isError, hasNextPage, fetchNextPage, isFetchingNextPage } = useJournal();
  const sentinel = useRef<HTMLDivElement>(null);

  // Défilement infini : la page suivante se charge quand le bas de la liste approche
  useEffect(() => {
    const target = sentinel.current;
    if (!target || !hasNextPage || typeof IntersectionObserver === "undefined") return;
    const observer = new IntersectionObserver(
      (seen) => {
        if (seen.some((s) => s.isIntersecting) && !isFetchingNextPage) fetchNextPage();
      },
      { rootMargin: "200px" }
    );
    observer.observe(target);
    return () => observer.disconnect();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  if (isLoading) return <Spinner label="Chargement du Journal..." />;
  if (isError) return <p className={styles.error}>Impossible de charger le Journal.</p>;

  const entries = data?.pages.flat() ?? [];
  if (entries.length === 0) return <p className={styles.empty}>Aucune fonctionnalité n'a encore été changée.</p>;

  return (
    <section aria-label="Journal">
      <ul className={styles.list}>
        {entries.map((entry) => (
          <Entry key={entry.id} entry={entry} />
        ))}
      </ul>
      <div ref={sentinel} />
      {isFetchingNextPage && <Spinner label="Chargement..." />}
      {hasNextPage && !isFetchingNextPage && (
        <Button label="Voir plus" variant="outline" onClick={() => fetchNextPage()} />
      )}
    </section>
  );
};

export default JournalPage;
