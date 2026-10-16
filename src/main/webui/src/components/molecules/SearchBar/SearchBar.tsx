import React from "react";
import Icon from "../../atoms/Icon/Icon";
import styles from "./SearchBar.module.css";

export interface SearchBarProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, "value" | "onChange" | "type"> {
  /** Libellé accessible, que le texte indicatif reprend par défaut. */
  label: string;
  value: string;
  onChange: (value: string) => void;
  /** Affiché à droite pendant une recherche, ex : « 3 résultat(s) ». */
  count?: React.ReactNode;
}

/** Barre de recherche : loupe, saisie, compteur de résultats et effacement en un clic. */
export const SearchBar: React.FC<SearchBarProps> = ({ label, value, onChange, count, placeholder, className, ...rest }) => {
  const searching = value.trim() !== "";
  return (
    <div className={`${styles.bar} ${className ?? ""}`} role="search">
      <Icon name="search" size={18} className={styles.icon} />
      <input
        type="search"
        className={styles.input}
        aria-label={label}
        placeholder={placeholder ?? `${label}…`}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        {...rest}
      />
      {searching && count !== undefined && <span className={styles.count}>{count}</span>}
      {searching && (
        <button type="button" className={styles.clear} aria-label="Effacer la recherche" onClick={() => onChange("")}>
          <Icon name="close" size={14} strokeWidth={2.5} />
        </button>
      )}
    </div>
  );
};

export default SearchBar;
