import React, { useState } from "react";
import AdminListItem, { AdminListItemProps } from "../../molecules/AdminListItem/AdminListItem";
import ConfirmDialog from "../../molecules/ConfirmDialog/ConfirmDialog";
import Button from "../../atoms/Button/Button";
import styles from "./AdminCrudList.module.css";

export type AdminItemDisplay = Omit<AdminListItemProps, "editing" | "onToggleEdit" | "children">;

export interface AdminCrudListProps<T, D> {
  /** Titre de la barre d'outils ; sans titre ni création, la liste est affichée seule. */
  title?: string;
  hint?: React.ReactNode;
  items: T[];
  idOf: (item: T) => string;
  display: (item: T, index: number) => AdminItemDisplay;
  toDraft: (item: T) => D;
  /** Champs du formulaire, partagés entre création (`item` absent) et édition. */
  renderFields: (draft: D, setDraft: (draft: D) => void, item?: T) => React.ReactNode;
  onUpdate: (id: string, draft: D) => Promise<unknown>;
  /** Actions propres à un élément, en bas à gauche de sa fiche ; « Enregistrer » passe alors à droite. */
  formActions?: (item: T) => React.ReactNode;
  create?: {
    buttonLabel: string;
    submitLabel: string;
    emptyDraft: D;
    onCreate: (draft: D) => Promise<unknown>;
  };
  remove?: {
    title: string;
    message: (item: T) => string;
    onRemove: (id: string) => Promise<unknown>;
  };
}

/**
 * Liste d'administration : dépliage d'un élément pour l'éditer, formulaire de
 * création, confirmation de suppression. Chaque page ne fournit que ses champs
 * et la conversion élément → brouillon ; le cycle d'édition vit ici.
 */
export function AdminCrudList<T, D>({
  title,
  hint,
  items,
  idOf,
  display,
  toDraft,
  renderFields,
  onUpdate,
  formActions,
  create,
  remove,
}: AdminCrudListProps<T, D>) {
  const [editingId, setEditingId] = useState<string | null>(null);
  const [draft, setDraft] = useState<D | null>(null);
  const [creating, setCreating] = useState(false);
  const [newDraft, setNewDraft] = useState<D | undefined>(create?.emptyDraft);
  const [toDeleteId, setToDeleteId] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  const run = async (action: () => Promise<unknown>, onSuccess: () => void) => {
    setPending(true);
    try {
      await action();
      onSuccess();
    } catch {
      // échec : le formulaire reste ouvert avec la saisie en cours
    } finally {
      setPending(false);
    }
  };

  const toggleEdit = (item: T) => {
    const id = idOf(item);
    if (editingId === id) {
      setEditingId(null);
      return;
    }
    setEditingId(id);
    setDraft(toDraft(item));
  };

  const handleSave = (id: string) => (e: React.FormEvent) => {
    e.preventDefault();
    if (draft === null) return;
    run(() => onUpdate(id, draft), () => setEditingId(null));
  };

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault();
    if (!create || newDraft === undefined) return;
    run(
      () => create.onCreate(newDraft),
      () => {
        setNewDraft(create.emptyDraft);
        setCreating(false);
      }
    );
  };

  const toDelete = items.find((item) => idOf(item) === toDeleteId);

  const list = (
    <ul className={styles.list}>
      {items.map((item, index) => {
        const id = idOf(item);
        const editing = editingId === id;
        return (
          <AdminListItem key={id} {...display(item, index)} editing={editing} onToggleEdit={() => toggleEdit(item)}>
            {editing && draft !== null && (
              <form className={styles.form} onSubmit={handleSave(id)} noValidate>
                {renderFields(draft, setDraft, item)}
                <div className={`${styles.formActions} ${formActions ? styles.split : ""}`}>
                  {formActions && <div className={styles.extraActions}>{formActions(item)}</div>}
                  <div className={styles.saveActions}>
                    <Button type="submit" label="Enregistrer" disabled={pending} />
                    {remove && (
                      <Button type="button" label="Supprimer" variant="danger" onClick={() => setToDeleteId(id)} disabled={pending} />
                    )}
                  </div>
                </div>
              </form>
            )}
          </AdminListItem>
        );
      })}
    </ul>
  );

  if (!title && !create && !remove) return list;

  return (
    <div className={styles.wrapper}>
      {(title || create) && (
        <div className={styles.toolbar}>
          {title && <h2 className={styles.title}>{title}</h2>}
          {create && (
            <Button
              label={creating ? "Annuler" : create.buttonLabel}
              variant={creating ? "outline" : "primary"}
              onClick={() => setCreating((v) => !v)}
            />
          )}
        </div>
      )}
      {hint && <p className={styles.hint}>{hint}</p>}

      {create && creating && newDraft !== undefined && (
        <form className={styles.form} onSubmit={handleCreate} noValidate>
          {renderFields(newDraft, setNewDraft)}
          <div className={styles.formActions}>
            <Button type="submit" label={create.submitLabel} disabled={pending} />
          </div>
        </form>
      )}

      {list}

      {remove && (
        <ConfirmDialog
          isOpen={toDeleteId !== null}
          title={remove.title}
          message={toDelete ? remove.message(toDelete) : ""}
          pending={pending}
          onCancel={() => setToDeleteId(null)}
          onConfirm={() => {
            if (!toDeleteId) return;
            run(
              () => remove.onRemove(toDeleteId),
              () => {
                setToDeleteId(null);
                setEditingId(null);
              }
            );
          }}
        />
      )}
    </div>
  );
}

export default AdminCrudList;
