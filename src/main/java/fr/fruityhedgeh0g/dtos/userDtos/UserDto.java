package fr.fruityhedgeh0g.dtos.userDtos;

import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonView;
import fr.fruityhedgeh0g.dtos.Views;
import fr.fruityhedgeh0g.dtos.sectorDtos.NestedSectorDto;
import fr.fruityhedgeh0g.enums.BureauFunctionEnum;
import fr.fruityhedgeh0g.enums.RoleEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class UserDto {

    @NotNull
    @JsonView({Views.Minimal.class,Views.Creation.class,Views.Update.class})
    @JsonAlias("user_id")
    UUID userId;

    @NotBlank(groups={Views.Creation.class})
    @JsonView({Views.Basic.class,Views.Creation.class,Views.Update.class})
    @JsonAlias({"first_name","updated_first_name"})
    String firstName;

    @NotBlank(groups={Views.Creation.class})
    @JsonView({Views.Basic.class,Views.Creation.class,Views.Update.class})
    @JsonAlias({"last_name","updated_last_name"})
    String lastName;

    @JsonView({Views.Detailed.class,Views.Update.class})
    String phone;

    /** Read only: their Keycloak account's e-mail, copied at each sign-in. */
    @JsonView({Views.Detailed.class,Views.Creation.class,Views.Update.class})
    String email;

    @JsonView(Views.Detailed.class)
    LocalDate birthDate;

    /** Read only: a Bénévole sans compte, recorded by the Bureau (ADR 0012). */
    @JsonView(Views.Basic.class)
    boolean withoutAccount;

    @JsonView(Views.Basic.class)
    RoleEnum role;

    /** Read only: Membre du Bureau or Président while sitting on the Bureau, none otherwise (ADR 0001). */
    @JsonView(Views.Basic.class)
    BureauFunctionEnum bureauFunction;

    /** Read only: the Secteur a person from Membre up belongs to, set by their promotion (ADR 0004). */
    @JsonView(Views.Basic.class)
    NestedSectorDto sector;

    //INFO : Retrait des Sets au profit d'une méthode dans EventServiceImpl retournant ces infos pour un couple Utilisateur/EventType
//    @JsonView(Views.Detailed.class)
//    Set<EventEntity> organizedEvents;
//
//    @JsonView(Views.Detailed.class)
//    Set<EventEntity> participatedEvents;
//
//    @JsonView(Views.Detailed.class)
//    Set<EventEntity> createdEvents;

}
