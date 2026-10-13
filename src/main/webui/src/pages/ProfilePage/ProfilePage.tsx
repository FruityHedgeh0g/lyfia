import React, { useState } from "react";
import { useAuth } from "../../auth/AuthContext";
import { ROLE_LABELS, roleAtLeast } from "../../auth/roles";
import { BUREAU_FUNCTION_LABELS } from "../../features/users/types";
import { useMonGroupe } from "../../features/events/useRoster";
import FormField from "../../components/molecules/FormField/FormField";
import Button from "../../components/atoms/Button/Button";
import Badge from "../../components/atoms/Badge/Badge";
import Icon from "../../components/atoms/Icon/Icon";
import styles from "./ProfilePage.module.css";

/** Le Groupe que la personne mène (Affectation), s'il y en a un. */
const ChefBadge: React.FC = () => {
  const { data } = useMonGroupe();
  return data?.group ? <Badge label={`Chef de groupe — ${data.group.name}`} tone="accent" /> : null;
};

export const ProfilePage: React.FC = () => {
  const { user, updateProfile, preview } = useAuth();
  const [phone, setPhone] = useState(user?.phone ?? "");
  const [saved, setSaved] = useState(false);
  const [saveError, setSaveError] = useState(false);

  if (!user) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setSaveError(false);
    updateProfile({ phone: phone.trim() }).then(
      () => setSaved(true),
      () => setSaveError(true)
    );
  };

  const initials = `${user.firstName.charAt(0)}${user.lastName.charAt(0)}`.toUpperCase();

  return (
    <div className={styles.layout}>
      <aside className={styles.identity}>
        <span className={styles.avatar} aria-hidden="true">
          {initials}
        </span>
        <p className={styles.name}>
          {user.firstName} {user.lastName}
        </p>
        <div className={styles.badges}>
          <Badge label={ROLE_LABELS[user.role]} />
          {user.sector && <Badge label={user.sector.name} tone="muted" />}
          {user.bureauFunction && <Badge label={BUREAU_FUNCTION_LABELS[user.bureauFunction]} tone="accent" />}
          {!preview && user.sector && roleAtLeast(user.role, "chef_de_groupe") && <ChefBadge />}
        </div>
      </aside>

      <section className={styles.panel}>
        <p className="eyebrow">Informations personnelles</p>
        <form className={styles.form} onSubmit={handleSubmit} noValidate>
          <div className={styles.row}>
            <FormField label="Prénom" value={user.firstName} disabled readOnly />
            <FormField label="Nom" value={user.lastName} disabled readOnly />
          </div>
          <FormField
            label="Téléphone"
            type="tel"
            autoComplete="tel"
            value={phone}
            onChange={(e) => {
              setPhone(e.target.value);
              setSaved(false);
            }}
          />
          <p className={styles.hint}>Nécessaire pour vous inscrire à un événement.</p>
          <p className={styles.hint}>Une erreur dans votre nom ? L'administrateur de votre secteur peut la corriger.</p>
          <div className={styles.actions}>
            <Button type="submit" label="Enregistrer" />
            {saved && (
              <p className={styles.saved}>
                <Icon name="check" size={18} strokeWidth={3} />
                Vos informations ont été mises à jour.
              </p>
            )}
            {saveError && <p role="alert">Vos informations n'ont pas pu être enregistrées. Réessayez.</p>}
          </div>
        </form>
      </section>
    </div>
  );
};

export default ProfilePage;
