package fr.fruityhedgeh0g.dtos.userDtos;

import java.time.LocalDate;

/**
 * What a person edits about themselves in Mon espace: their phone number and birth date (left out, the birth date is
 * kept); their names are an Admin's (ADR 0007), their e-mail their Keycloak account's.
 */
public record ProfileDto(String phone, LocalDate birthDate) {
}
