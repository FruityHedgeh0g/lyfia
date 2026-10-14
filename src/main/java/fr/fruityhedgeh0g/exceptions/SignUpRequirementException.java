package fr.fruityhedgeh0g.exceptions;

/**
 * Signing up for an Event needs something the person has not given yet; the front end reads {@code code} to ask for
 * it: {@code birth-date-required}, {@code decharge-requise} (a minor's legal representative approves the décharge) or
 * {@code decharge-indisponible} (no décharge uploaded yet, so minors cannot sign up).
 */
public class SignUpRequirementException extends RuntimeException {

    public static final String BIRTH_DATE_REQUIRED = "birth-date-required";
    public static final String DECHARGE_REQUISE = "decharge-requise";
    public static final String DECHARGE_INDISPONIBLE = "decharge-indisponible";

    private final String code;

    public SignUpRequirementException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
