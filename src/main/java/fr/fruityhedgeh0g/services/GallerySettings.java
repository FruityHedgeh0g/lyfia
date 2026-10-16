package fr.fruityhedgeh0g.services;

import fr.fruityhedgeh0g.exceptions.InvalidResourceException;
import fr.fruityhedgeh0g.repositories.ConfigurationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The largest Photo de la Galerie kept, set by the Super admin (ADR 0014): the browser scales a larger one down
 * before sending it. Known even before the Super admin sets them, with their default.
 */
@ApplicationScoped
public class GallerySettings {

    public static final String MAX_PX = "galerie.photo.max-px";
    public static final String MAX_MO = "galerie.photo.max-mo";

    /** The settings and their defaults: 3000 px on the long side, 5 MB. */
    public static final Map<String, String> DEFAULTS = new LinkedHashMap<>(Map.of(MAX_PX, "3000", MAX_MO, "5"));

    /** What the Super admin may set: a long side between 800 and 10000 px, a weight between 1 and 20 MB. */
    public static void validate(String name, String value) {
        if (!DEFAULTS.containsKey(name)) return;
        int number;
        try {
            number = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new InvalidResourceException(name + " is a whole number: " + value);
        }
        boolean fits = name.equals(MAX_PX) ? number >= 800 && number <= 10000 : number >= 1 && number <= 20;
        if (!fits) throw new InvalidResourceException(name + " is out of range: " + value);
    }

    @Inject
    ConfigurationRepository configurationRepository;

    public record Limits(int maxPx, int maxMo) {
        public long maxBytes() {
            return (long) maxMo * 1024 * 1024;
        }
    }

    public Limits limits() {
        return new Limits(read(MAX_PX), read(MAX_MO));
    }

    private int read(String name) {
        return configurationRepository.findByName(name)
                .map(c -> c.getValue().trim())
                .filter(v -> v.matches("[0-9]+"))
                .map(Integer::parseInt)
                .orElse(Integer.parseInt(DEFAULTS.get(name)));
    }
}
