import React from "react";
import { NavLink, Outlet, useLocation } from "react-router-dom";
import { useAccess } from "../../../auth/useAccess";
import { AccessEntry, AccessSection } from "../../../auth/access";
import PageHero from "../../organisms/PageHero/PageHero";
import DropdownMenu from "../../molecules/DropdownMenu/DropdownMenu";
import styles from "./SpaceLayout.module.css";

const navLinkClass = ({ isActive }: { isActive: boolean }) => `${styles.tab}${isActive ? ` ${styles.active}` : ""}`;

export interface SpaceLayoutProps {
  section: AccessSection;
  eyebrow: string;
  title: string;
  lead?: React.ReactNode;
}

/** Les entrées dans l'ordre de la carte d'accès, celles d'un même thème réunies à la place de la première. */
type NavItem = { entry: AccessEntry & { id: string } } | { group: string; entries: (AccessEntry & { id: string })[] };

function navItems(entries: (AccessEntry & { id: string })[]): NavItem[] {
  const items: NavItem[] = [];
  entries.forEach((entry) => {
    if (!entry.group) return items.push({ entry });
    const existing = items.find((item) => "group" in item && item.group === entry.group);
    if (existing && "group" in existing) existing.entries.push(entry);
    else items.push({ group: entry.group, entries: [entry] });
  });
  return items;
}

/**
 * Gabarit des espaces "Mon espace" et "Administration" : bandeau, onglets issus de la carte d'accès, contenu. Les
 * entrées d'un thème (Organisation, Contenu, Site) se regroupent dans un onglet déroulant, qui nomme la page ouverte :
 * tous les onglets tiennent sur une ligne, même pour le Super admin.
 */
export const SpaceLayout: React.FC<SpaceLayoutProps> = ({ section, eyebrow, title, lead }) => {
  const { navFor } = useAccess();
  const { pathname } = useLocation();
  const items = navItems(navFor(section));
  const grouped = items.some((item) => "group" in item);

  return (
    <>
      <PageHero compact eyebrow={eyebrow} title={title} lead={lead} />
      <div className={styles.tabsBar}>
        <nav className={`${styles.tabs}${grouped ? ` ${styles.grouped}` : ""}`} aria-label={title}>
          {items.map((item) =>
            "group" in item ? (
              <DropdownMenu
                key={item.group}
                label={[item.group, item.entries.find((e) => pathname.startsWith(e.path))?.label].filter(Boolean).join(" · ")}
                items={item.entries.map((e) => ({ label: e.label, to: e.path }))}
                triggerClassName={`${styles.tab} ${styles.groupTab}`}
                activeClassName={styles.active}
              />
            ) : (
              <NavLink key={item.entry.id} className={navLinkClass} to={item.entry.path} end={item.entry.end}>
                {item.entry.label}
              </NavLink>
            )
          )}
        </nav>
      </div>
      <div className={styles.content}>
        <Outlet />
      </div>
    </>
  );
};

export default SpaceLayout;
