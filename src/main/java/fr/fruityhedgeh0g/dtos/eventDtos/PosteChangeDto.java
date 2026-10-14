package fr.fruityhedgeh0g.dtos.eventDtos;

import fr.fruityhedgeh0g.enums.PosteEnum;

/** The Poste the Bureau gives someone at an Event. */
public record PosteChangeDto(PosteEnum poste) {
}
