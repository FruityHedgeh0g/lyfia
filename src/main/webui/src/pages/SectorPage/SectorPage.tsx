import React, { useEffect, useState } from "react";
import { useSector, useSectorMutations } from "../../features/sectors/useSector";
import { useAuth } from "../../auth/AuthContext";
import { useGroups } from "../../features/groups/useGroups";
import FormField from "../../components/molecules/FormField/FormField";
import Button from "../../components/atoms/Button/Button";
import Spinner from "../../components/atoms/Spinner/Spinner";
import styles from "./SectorPage.module.css";

/**
 * Mon secteur (Bureau) : le Secteur de la personne, sa description, que le
 * Bureau tient à jour (seul le Super admin le renomme, depuis Administration →
 * Secteurs), et ses Groupes, avec leur Chef de groupe et la partie du Secteur
 * qu'ils couvrent. Le compte Super admin, sans Secteur, n'a pas cette page.
 */
export const SectorPage: React.FC = () => {
  const { user } = useAuth();
  const sectorId = user?.sector?.sectorId;
  const { data: sector, isLoading, isError } = useSector(sectorId);
  const { data: groups, isLoading: groupsLoading } = useGroups();
  const { update: updateSector } = useSectorMutations();

  const [description, setDescription] = useState("");
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (sector) {
      setDescription(sector.description);
      setSaved(false);
    }
  }, [sector]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!sectorId) return;
    updateSector.mutate({ sectorId, name: sector?.name ?? "", description }, { onSuccess: () => setSaved(true) });
  };

  if (isLoading) return <Spinner label="Chargement du secteur..." />;
  if (!sectorId) return <p className={styles.empty}>Aucun secteur pour le moment.</p>;
  if (isError || !sector) return <p className={styles.error}>Impossible de charger le secteur.</p>;

  const sectorGroups = (groups ?? []).filter((g) => g.sectorId === sector.sectorId);

  return (
    <div className={styles.wrapper}>
      <section className={styles.panel}>
        <h2>{sector.name}</h2>
        <form className={styles.form} onSubmit={handleSubmit} noValidate>
          <FormField
            label="Description"
            multiline
            rows={4}
            value={description}
            onChange={(e) => {
              setDescription(e.target.value);
              setSaved(false);
            }}
          />
          <Button type="submit" label={updateSector.isPending ? "Enregistrement..." : "Enregistrer"} disabled={updateSector.isPending} />
          {saved && <p className={styles.saved}>Les informations du secteur ont été mises à jour.</p>}
        </form>
      </section>

      <section className={styles.panel}>
        <h2>Groupes du secteur</h2>
        {groupsLoading ? (
          <Spinner label="Chargement des groupes..." />
        ) : sectorGroups.length === 0 ? (
          <p className={styles.empty}>Aucun groupe dans ce secteur pour le moment.</p>
        ) : (
          <ul className={styles.groupList} aria-label="Groupes du secteur">
            {sectorGroups.map((group) => (
              <li key={group.groupId} className={styles.group}>
                <span className={styles.groupName}>{group.name}</span>
                <span className={styles.groupMeta}>
                  {[group.area, group.chef ? `Chef : ${group.chef.firstName} ${group.chef.lastName}` : "Sans chef de groupe"]
                    .filter(Boolean)
                    .join(" · ")}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
};

export default SectorPage;
