import React, { useState } from "react";
import Modal from "../../components/molecules/Modal/Modal";
import FormField from "../../components/molecules/FormField/FormField";
import Checkbox from "../../components/atoms/Checkbox/Checkbox";
import Button from "../../components/atoms/Button/Button";
import Spinner from "../../components/atoms/Spinner/Spinner";
import { dechargeUrl } from "./dechargeApi";
import { useCurrentDecharge } from "./useDecharge";
import styles from "./DechargeModal.module.css";

interface DechargeModalProps {
  isOpen: boolean;
  onClose: () => void;
  /** Le représentant légal a approuvé : on inscrit, en gardant son nom pour l'audit. */
  onApprove: (signataire: string) => void;
  pending: boolean;
}

/**
 * La décharge de responsabilité qu'un représentant légal approuve pour inscrire un mineur : le document en vigueur à
 * lire, son accord, et son prénom et son nom, gardés avec la version approuvée.
 */
export const DechargeModal: React.FC<DechargeModalProps> = ({ isOpen, onClose, onApprove, pending }) => {
  const { data: document, isLoading } = useCurrentDecharge(isOpen);
  const [approved, setApproved] = useState(false);
  const [signataire, setSignataire] = useState("");

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Décharge de responsabilité">
      <div className={styles.body}>
        <p>Vous êtes mineur à la date de cet événement : votre représentant légal doit lire et approuver la décharge de responsabilité.</p>
        {isLoading ? (
          <Spinner label="Chargement de la décharge..." />
        ) : (
          document && (
            <a className={styles.document} href={dechargeUrl(document.documentId)} target="_blank" rel="noreferrer">
              Lire la décharge ({document.originalFilename})
            </a>
          )
        )}
        <Checkbox
          label="J'ai lu la décharge et je l'approuve en tant que représentant légal."
          checked={approved}
          onChange={setApproved}
        />
        <FormField
          label="Prénom et nom du représentant légal"
          value={signataire}
          autoComplete="off"
          onChange={(e) => setSignataire(e.target.value)}
        />
        <div className={styles.actions}>
          <Button type="button" label="Annuler" variant="outline" onClick={onClose} />
          <Button
            type="button"
            label="Approuver et m'inscrire"
            variant="accent"
            disabled={pending || !approved || !signataire.trim() || !document}
            onClick={() => onApprove(signataire.trim())}
          />
        </div>
      </div>
    </Modal>
  );
};

export default DechargeModal;
