import { FeatureFlag, FeatureName, JournalEntry } from "./types";
import { apiFetch } from "../../lib/http";

/**
 * Les Fonctionnalités, sur FeatureController : lues par tous (le site public s'y conforme), activées et
 * désactivées par le seul Super admin, chaque changement allant au Journal (ADR 0009).
 */
export const fetchFeatureFlags = () => apiFetch<FeatureFlag[]>("/api/features");

export const setFeatureFlagActive = (name: FeatureName, isActive: boolean, reason?: string) =>
  apiFetch<FeatureFlag>(`/api/features/${encodeURIComponent(name)}`, {
    method: "PUT",
    body: JSON.stringify({ isActive, reason: reason?.trim() || undefined }),
  });

/** Nombre d'entrées du Journal chargées à la fois. */
export const JOURNAL_PAGE_SIZE = 20;

/** Une page du Journal, les plus récentes d'abord ; réservé au Super admin. */
export const fetchJournal = (page: number) =>
  apiFetch<JournalEntry[]>(`/api/features/journal?page=${page}&size=${JOURNAL_PAGE_SIZE}`);
