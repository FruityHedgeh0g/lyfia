package fr.fruityhedgeh0g.dtos.featureDtos;

/** Turns a feature on or off, with an optional reason for the Journal. */
public record FeatureSwitchDto(Boolean isActive, String reason) {
}
