package fr.fruityhedgeh0g.services;


import fr.fruityhedgeh0g.dtos.groupDtos.GroupDto;
import fr.fruityhedgeh0g.dtos.sectorDtos.SectorDto;
import fr.fruityhedgeh0g.dtos.userDtos.UserDto;
import fr.fruityhedgeh0g.entities.GroupEntity;
import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.entities.configurations.FeatureEntity;
import fr.fruityhedgeh0g.enums.RoleEnum;
import fr.fruityhedgeh0g.exceptions.DuplicateResourceException;
import fr.fruityhedgeh0g.exceptions.InvalidResourceException;
import fr.fruityhedgeh0g.exceptions.UnknownResourceException;
import fr.fruityhedgeh0g.repositories.UserRepository;
import fr.fruityhedgeh0g.utilities.mappers.UserMapper;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;

import static org.mockito.Mockito.*;


@QuarkusTest
@TestSecurity(user = "super-admin", roles = "super_admin")
@TestTransaction
class UserServiceTest {
    @Inject
    UserRepository userRepository;

    @Inject
    UserServiceImpl userService;

    @Inject
    UserMapper userMapper;

    @BeforeEach
    public void setUp() {

    }

    /** @see UserServiceImpl#listAll() () **/

    @Test
    @TestTransaction
    public void listAllUsers_Success(){
        UserEntity firstUser = UserEntity.builder().birthDate(java.time.LocalDate.of(1990, 1, 1))
                .userId(UUID.randomUUID())
                .firstName("Platy")
                .lastName("Pus")
                .build();
        userRepository.persist(firstUser);

        UserEntity secondUser = UserEntity.builder().birthDate(java.time.LocalDate.of(1990, 1, 1))
                .userId(UUID.randomUUID())
                .firstName("Hedge")
                .lastName("Hog")
                .build();
        userRepository.persist(secondUser);

        List<UserEntity> comparativeUsers = List.of(firstUser,secondUser);
        List<UserEntity> gatheredUsers = userService.listAll().stream().map(userMapper::toEntity).toList();

        Assertions.assertEquals(comparativeUsers.size(), gatheredUsers.size());
        Assertions.assertIterableEquals(comparativeUsers, gatheredUsers);
    }

    @Test
    @TestTransaction
    public void getAllUsers_NotFound(){
        List<UserDto> users = new ArrayList<>();
        List<UserDto> gatheredUsers = userService.listAll();
        Assertions.assertEquals(0,gatheredUsers.size());
        Assertions.assertEquals(users, gatheredUsers);
    }

    /** @see UserServiceImpl#getById(UUID) () **/

    @Test
    @TestTransaction
    public void getById_Success(){
        UserEntity user = UserEntity.builder().birthDate(java.time.LocalDate.of(1990, 1, 1))
                .userId(UUID.randomUUID())
                .firstName("Platy")
                .lastName("Pus")
                .build();
        userRepository.persist(user);

        UserEntity retrievedUser = userMapper.toEntity(userService.getById(user.getUserId()));
        Assertions.assertEquals(user, retrievedUser);

    }

    @Test
    @TestTransaction
    public void getById_NotFound(){
        Assertions.assertThrows(UnknownResourceException.class, () -> userService.getById(UUID.randomUUID()));
    }

    @Test
    @TestTransaction
    public void getById_NullId(){
        Assertions.assertThrows(ConstraintViolationException.class, () -> userService.getById(null));
    }

    /** @see UserServiceImpl#doCreate(UserDto) () **/

    @Test
    @TestTransaction
    public void create_NullDto(){
        Assertions.assertThrows(ConstraintViolationException.class, () -> userService.doCreate(null));
    }

    @Test
    @TestTransaction
    public void create_Success(){
        UserDto userDto = userService.doCreate(
                UserDto.builder()
                        .userId(UUID.randomUUID())
                        .firstName("Platy")
                        .lastName("Pus")
                        .build()
                );

        UserDto retrievedUser = userService.getById(userDto.getUserId());
        Assertions.assertEquals(userDto,retrievedUser);
    }

    @Test
    @TestTransaction
    public void create_NewPersonIsBenevole(){
        UserDto userDto = userService.doCreate(
                UserDto.builder()
                        .userId(UUID.randomUUID())
                        .firstName("Platy")
                        .lastName("Pus")
                        .role(RoleEnum.ADMIN)
                        .build()
        );

        Assertions.assertEquals(RoleEnum.BENEVOLE, userService.getById(userDto.getUserId()).getRole());
    }

    @Test
    @TestTransaction
    public void create_Duplicate(){
        UserEntity user = UserEntity.builder().birthDate(java.time.LocalDate.of(1990, 1, 1))
                .userId(UUID.randomUUID())
                .firstName("Platy")
                .lastName("Pus")
                .build();
        userRepository.persist(user);

        Assertions.assertTrue(userRepository.existsById(user.getUserId()));

        Assertions.assertThrows(DuplicateResourceException.class,
                () -> userService.doCreate(userMapper.toDto(user))
        );

    }

    /** @see UserServiceImpl#doUpdate(UserDto) () **/

    @Test
    @TestTransaction
    public void update_Success(){
        UserEntity user = UserEntity.builder().birthDate(java.time.LocalDate.of(1990, 1, 1))
                .userId(UUID.randomUUID())
                .firstName("Platy")
                .lastName("Pus")
                .build();
        userRepository.persist(user);

        Assertions.assertTrue(userRepository.existsById(user.getUserId()));

        UserDto updatedUser = UserDto.builder()
                .userId(user.getUserId())
                .firstName("Hedge")
                .lastName("Hog")
                .build();

        Assertions.assertDoesNotThrow(() -> userService.doUpdate(updatedUser));

        // Names belong to the database once set (ADR 0007): Keycloak does not overwrite them
        Assertions.assertEquals("Platy", userRepository.findById(user.getUserId()).getFirstName());

    }

    @Test
    @TestTransaction
    public void update_KeepsRole(){
        UserEntity user = UserEntity.builder().birthDate(java.time.LocalDate.of(1990, 1, 1))
                .userId(UUID.randomUUID())
                .firstName("Platy")
                .lastName("Pus")
                .role(RoleEnum.MEMBRE)
                .build();
        userRepository.persist(user);

        userService.doUpdate(UserDto.builder().userId(user.getUserId()).firstName("Hedge").role(RoleEnum.ADMIN).build());

        Assertions.assertEquals(RoleEnum.MEMBRE, userRepository.findById(user.getUserId()).getRole());
    }

    @Test
    @TestTransaction
    public void update_NotFound(){
        UserDto user = UserDto.builder()
                .userId(UUID.randomUUID())
                .firstName("Platy")
                .lastName("Pus")
                .build();

        Assertions.assertThrows(UnknownResourceException.class, () -> userService.doUpdate(user));

    }

    @Test
    @TestTransaction
    public void update_NullDto(){
        Assertions.assertThrows(ConstraintViolationException.class, () -> userService.doUpdate(null));
    }

    /** @see UserServiceImpl#doDelete(UUID) () **/

    @Test
    @TestTransaction
    public void delete_NullId(){
        Assertions.assertThrows(ConstraintViolationException.class, () -> userService.doDelete(null));
    }

    @Test
    @TestTransaction
    @Disabled("User deletion not implemented yet")
    public void delete_NotFound(){
        Assertions.assertThrows(UnknownResourceException.class, () -> userService.doDelete(UUID.randomUUID()));
    }

    @Test
    @TestTransaction
    @Disabled("User deletion not implemented yet")
    public void delete_Success(){

        UserEntity user = UserEntity.builder().birthDate(java.time.LocalDate.of(1990, 1, 1))
                .userId(UUID.randomUUID())
                .firstName("Platy")
                .lastName("Pus")
                .build();
        userRepository.persist(user);

        Assertions.assertDoesNotThrow(() -> userService.doDelete(user.getUserId()));
        Assertions.assertThrows(UnknownResourceException.class, () -> userService.getById(user.getUserId()));
    }


}
