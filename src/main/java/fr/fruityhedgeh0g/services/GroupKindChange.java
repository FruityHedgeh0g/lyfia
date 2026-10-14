package fr.fruityhedgeh0g.services;

import java.util.UUID;

/**
 * A Groupe changed kind (CDI event, fired within the change's transaction). The Events of its Secteur refuse it while
 * anyone but the Chef is with it or asks for it, and move the Chef to the Poste of the new kind.
 */
public record GroupKindChange(UUID groupId) {
}
