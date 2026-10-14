package fr.fruityhedgeh0g.services;

import java.util.UUID;

/**
 * A Groupe's Affectation changed (CDI event, fired within the change's transaction): {@code previousChefId} led it
 * before, or none. The Events of its Secteur follow it: the Chef rides with the Groupe they lead (ADR 0010).
 */
public record AffectationChange(UUID groupId, UUID previousChefId) {
}
