import React, { useState } from "react";
import { Link } from "react-router-dom";
import Logo from "../../atoms/Logo/Logo";
import Icon from "../../atoms/Icon/Icon";
import DevelopersSheet from "../../molecules/DevelopersSheet/DevelopersSheet";
import { useAccess } from "../../../auth/useAccess";
import styles from "./Footer.module.css";

const CONTACT_EMAIL = "contact@urue-algrange.fr";

export const Footer: React.FC = () => {
  const { mainNav } = useAccess();
  const groups = mainNav();
  const menus = groups.filter((g) => g.kind === "menu");
  const quickLinks = groups.flatMap((g) => (g.kind === "link" ? [g.entry] : []));
  const [developersOpen, setDevelopersOpen] = useState(false);

  return (
    <footer className={styles.footer}>
      <div className={styles.slogan} aria-hidden="true">
        <span>Une rose</span>
        <span className={styles.sloganAccent}>un espoir</span>
      </div>

      <div className={styles.inner}>
        <div className={styles.brand}>
          <Logo inverse />
          <p className={styles.pitch}>
            Association loi 1901 basée à Algrange. Des motards mobilisés toute l'année contre le cancer, aux côtés des
            malades et de leurs proches.
          </p>
        </div>

        <nav className={styles.columns} aria-label="Plan du site">
          {menus.map((group) =>
            group.kind === "menu" ? (
              <div key={group.label} className={styles.column}>
                <h2 className={styles.columnTitle}>{group.label}</h2>
                <ul>
                  {group.entries.map((e) => (
                    <li key={e.id}>
                      <Link to={e.path}>{e.label}</Link>
                    </li>
                  ))}
                </ul>
              </div>
            ) : null
          )}
          {quickLinks.length > 0 && (
            <div className={styles.column}>
              <h2 className={styles.columnTitle}>Accès rapide</h2>
              <ul>
                {quickLinks.map((e) => (
                  <li key={e.id}>
                    <Link to={e.path}>{e.label}</Link>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </nav>

        <div className={styles.contact}>
          <h2 className={styles.columnTitle}>Nous écrire</h2>
          <a className={styles.mail} href={`mailto:${CONTACT_EMAIL}`}>
            <Icon name="mail" size={18} />
            {CONTACT_EMAIL}
          </a>
          <p className={styles.place}>
            <Icon name="pin" size={18} />
            Algrange, Moselle
          </p>
        </div>
      </div>

      <div className={styles.bottom}>
        <p>© {new Date().getFullYear()} Une Rose Un Espoir - Algrange · Association loi 1901</p>
        <button type="button" className={styles.developers} onClick={() => setDevelopersOpen(true)}>
          Voir les développeurs
        </button>
        <p className={styles.united}>Tous unis contre le cancer</p>
      </div>

      {developersOpen && <DevelopersSheet onClose={() => setDevelopersOpen(false)} />}
    </footer>
  );
};

export default Footer;
