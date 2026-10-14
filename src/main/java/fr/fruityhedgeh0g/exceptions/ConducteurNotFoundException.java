package fr.fruityhedgeh0g.exceptions;

/**
 * The name a passager typed matches no conducteur signed up for the chosen Journées, or several about as well
 * ({@code ambiguous}): the front end says which, without ever showing who is signed up.
 */
public class ConducteurNotFoundException extends RuntimeException {

    private final boolean ambiguous;

    public ConducteurNotFoundException(String message, boolean ambiguous) {
        super(message);
        this.ambiguous = ambiguous;
    }

    public boolean isAmbiguous() {
        return ambiguous;
    }
}
