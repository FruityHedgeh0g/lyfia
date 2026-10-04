package fr.fruityhedgeh0g.exceptions;

import fr.fruityhedgeh0g.enums.FeatureEnum;
import lombok.Getter;

/** What was asked belongs to a Feature that is turned off (ADR 0009). */
@Getter
public class FeatureOffException extends RuntimeException {
    private final FeatureEnum feature;

    public FeatureOffException(FeatureEnum feature) {
        super("Feature turned off: " + feature.id());
        this.feature = feature;
    }
}
