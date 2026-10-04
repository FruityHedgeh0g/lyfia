import React, { useEffect, useState } from "react";
import { NavLink, useLocation } from "react-router-dom";
import Logo from "../../atoms/Logo/Logo";
import Icon from "../../atoms/Icon/Icon";
import ButtonLink from "../../atoms/ButtonLink/ButtonLink";
import DropdownMenu from "../../molecules/DropdownMenu/DropdownMenu";
import ThemeToggle from "../../molecules/ThemeToggle/ThemeToggle";
import SecteurSelect from "../../molecules/SecteurSelect/SecteurSelect";
import { useAuth } from "../../../auth/AuthContext";
import { useAccess } from "../../../auth/useAccess";
import { entry } from "../../../auth/access";
import styles from "./Header.module.css";

const navLinkClass = ({ isActive }: { isActive: boolean }) => `${styles.navLink}${isActive ? ` ${styles.active}` : ""}`;

export const Header: React.FC = () => {
  const [menuOpen, setMenuOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);
  const { isAuthenticated, login, logout } = useAuth();
  const { mainNav, canAccess } = useAccess();
  const location = useLocation();
  const groups = mainNav();

  // Hystérésis : replier le header réduit sa hauteur (~56px) et décale le scroll.
  // Un seuil unique ferait osciller l'état ; l'écart entre les deux seuils doit dépasser ce delta.
  useEffect(() => {
    const onScroll = () =>
      setScrolled((prev) => (prev ? window.scrollY > 8 : window.scrollY > 96));
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  // Le tiroir mobile se referme à chaque navigation.
  useEffect(() => {
    setMenuOpen(false);
  }, [location.pathname, location.hash]);

  useEffect(() => {
    if (!menuOpen) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") setMenuOpen(false);
    };
    document.addEventListener("keydown", onKeyDown);
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      document.body.style.overflow = "";
    };
  }, [menuOpen]);

  const handleAuthClick = () => (isAuthenticated ? logout() : login());

  return (
    <header className={`${styles.header}${scrolled ? ` ${styles.scrolled}` : ""}`}>
      <div className={styles.topbar}>
        <div className={styles.inner}>
          <p className={styles.tagline}>
            <Icon name="heart" size={14} className={styles.heart} />
            Tous unis contre le cancer
          </p>
          <div className={styles.utils}>
            <SecteurSelect />
            <button
              type="button"
              className={styles.loginBtn}
              onClick={handleAuthClick}
              aria-label={isAuthenticated ? "Se déconnecter" : "Se connecter"}
            >
              <Icon name="user" size={15} />
              <span aria-hidden="true">{isAuthenticated ? "Se déconnecter" : "Se connecter"}</span>
            </button>
            <ThemeToggle />
          </div>
        </div>
      </div>

      <div className={styles.bar}>
        <div className={styles.inner}>
          <Logo />

          <nav className={styles.nav} aria-label="Principal">
            {groups.map((group) =>
              group.kind === "menu" ? (
                <DropdownMenu key={group.label} label={group.label} items={group.entries.map((e) => ({ label: e.label, to: e.path }))} />
              ) : (
                <NavLink key={group.entry.id} className={navLinkClass} to={group.entry.path} end={group.entry.end}>
                  {group.entry.label}
                </NavLink>
              )
            )}
          </nav>

          <div className={styles.actions}>
            {canAccess("donation") && (
              <ButtonLink className={styles.donate} to={entry("donation").path} label="Faire un don" variant="accent" />
            )}
            <button
              type="button"
              className={styles.burger}
              aria-label={menuOpen ? "Fermer le menu" : "Ouvrir le menu"}
              aria-expanded={menuOpen}
              aria-controls="mobile-nav"
              onClick={() => setMenuOpen((v) => !v)}
            >
              <Icon name={menuOpen ? "close" : "menu"} size={24} />
            </button>
          </div>
        </div>
      </div>

      {menuOpen && (
        <>
          <div className={styles.scrim} onClick={() => setMenuOpen(false)} aria-hidden="true" />
          <nav id="mobile-nav" className={styles.drawer} aria-label="Principal">
            <button type="button" className={styles.drawerClose} aria-label="Fermer le menu" onClick={() => setMenuOpen(false)}>
              <Icon name="close" size={26} />
            </button>
            {groups.map((group) =>
              group.kind === "menu" ? (
                <div key={group.label} className={styles.drawerGroup}>
                  <p className={styles.drawerLabel}>{group.label}</p>
                  {group.entries.map((e) => (
                    <NavLink key={e.id} className={styles.drawerLink} to={e.path}>
                      {e.label}
                    </NavLink>
                  ))}
                </div>
              ) : (
                <NavLink key={group.entry.id} className={`${styles.drawerLink} ${styles.drawerTop}`} to={group.entry.path}>
                  {group.entry.label}
                </NavLink>
              )
            )}
            {canAccess("donation") && (
              <ButtonLink className={styles.drawerCta} to={entry("donation").path} label="Faire un don" variant="accent" arrow />
            )}
          </nav>
        </>
      )}
    </header>
  );
};

export default Header;
