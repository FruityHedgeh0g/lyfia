package fr.fruityhedgeh0g.controllers;

import java.util.Locale;

import java.time.format.DateTimeFormatter;

import java.time.LocalDate;

import fr.fruityhedgeh0g.entities.EventEntity;
import fr.fruityhedgeh0g.entities.EventRegistrationEntity;
import fr.fruityhedgeh0g.entities.GroupEntity;
import fr.fruityhedgeh0g.entities.SectorEntity;
import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.EventStatusEnum;
import fr.fruityhedgeh0g.enums.RoleEnum;
import fr.fruityhedgeh0g.repositories.EventRegistrationRepository;
import fr.fruityhedgeh0g.repositories.EventRepository;
import fr.fruityhedgeh0g.repositories.GroupRepository;
import fr.fruityhedgeh0g.repositories.SectorRepository;
import fr.fruityhedgeh0g.repositories.UserRepository;
import fr.fruityhedgeh0g.security.DatabaseRoleAugmentor;
import fr.fruityhedgeh0g.utilities.export.RosterSpreadsheet;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import jakarta.inject.Inject;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Bureau's spreadsheet of an Event: one sheet per Journée, Participants then the Liste d'attente (ADR 0011). */
@QuarkusTest
@TestHTTPEndpoint(EventController.class)
public class RosterExportResourceTest {

    static final String HEADER = "Nom|Prénom|Téléphone|Poste|Groupe|Avec|Place|E-mail|Naissance|Décharge";

    static final String ANNE_ID = "00000000-0000-0000-0010-000000000001";
    static final String PAUL_ID = "00000000-0000-0000-0010-000000000002";
    static final String CLAIRE_ID = "00000000-0000-0000-0010-000000000003";
    static final String DENIS_ID = "00000000-0000-0000-0010-000000000004";
    static final String EVE_ID = "00000000-0000-0000-0010-000000000005";
    static final String CHEF_ID = "00000000-0000-0000-0010-000000000006";
    static final String BUREAU_ID = "00000000-0000-0000-0010-000000000007";

    @Inject
    UserRepository userRepository;

    @Inject
    SectorRepository sectorRepository;

    @Inject
    GroupRepository groupRepository;

    @Inject
    EventRepository eventRepository;

    @Inject
    EventRegistrationRepository registrationRepository;

    private final List<UUID> persons = new ArrayList<>();
    private UUID sector;
    private UUID nord;
    private UUID sud;
    private UUID event;
    private int signUps;

    @BeforeEach
    void seed() {
        persistPerson(ANNE_ID, RoleEnum.BENEVOLE, "Anne", "Martin", "06 01 01 01 01");
        persistPerson(PAUL_ID, RoleEnum.BENEVOLE, "Paul", "Martin", "06 02 02 02 02");
        persistPerson(CLAIRE_ID, RoleEnum.BENEVOLE, "Claire", "Weber", "06 03 03 03 03");
        persistPerson(DENIS_ID, RoleEnum.BENEVOLE, "Denis", "Klein", "06 04 04 04 04");
        persistPerson(EVE_ID, RoleEnum.BENEVOLE, "Eve", "Klein", "06 05 05 05 05");
        persistPerson(CHEF_ID, RoleEnum.CHEF_DE_GROUPE, "Chef", "Nord", "06 06 06 06 06");
        persistPerson(BUREAU_ID, RoleEnum.BUREAU, "Bureau", "Membre", "06 07 07 07 07");
        QuarkusTransaction.requiringNew().run(() -> {
            SectorEntity s = SectorEntity.builder().name("Test Secteur " + UUID.randomUUID()).build();
            sectorRepository.persist(s);
            sector = s.getSectorId();
            nord = persistGroup("Test Nord", s);
            sud = persistGroup("Test Sud", s);

            EventEntity e = new EventEntity();
            e.setName("Test Balade");
            e.setStatus(EventStatusEnum.OUVERT);
            e.setStartDateTime(LocalDateTime.now(EventEntity.ZONE).plusDays(1));
            e.setEndDateTime(LocalDateTime.now(EventEntity.ZONE).plusDays(2));
            e.setSector(s);
            eventRepository.persist(e);
            event = e.getEventId();
        });

        // Nord: Anne with her passager Paul; no Groupe: Claire; Liste d'attente: Denis with his passager Eve
        EventRegistrationEntity anne = conducteur(ANNE_ID, false);
        QuarkusTransaction.requiringNew().run(() -> registrationRepository.findById(anne.getRegistrationId()).placeIn(groupRepository.findById(nord)));
        passager(PAUL_ID, ANNE_ID);
        conducteur(CLAIRE_ID, false);
        conducteur(DENIS_ID, true);
        passager(EVE_ID, DENIS_ID);
        SecteurFixtures.attachToSecteur(userRepository, sectorRepository, sector);
    }

    @AfterEach
    void cleanUp() {
        SecteurFixtures.detachFromSecteur(userRepository, sectorRepository, sector);
        QuarkusTransaction.requiringNew().run(() -> {
            registrationRepository.delete("event.sector.sectorId", sector);
            eventRepository.list("sector.sectorId", sector).forEach(eventRepository::delete);
            groupRepository.delete("sector.sectorId", sector);
            sectorRepository.deleteById(sector);
        });
        QuarkusTransaction.requiringNew().run(() -> persons.forEach(userRepository::deleteById));
        persons.clear();
    }

    private void persistPerson(String id, RoleEnum role, String firstName, String lastName, String phone) {
        UUID userId = UUID.fromString(id);
        QuarkusTransaction.requiringNew().run(() -> userRepository.persist(
                UserEntity.builder().birthDate(java.time.LocalDate.of(1990, 1, 1)).userId(userId).firstName(firstName).lastName(lastName).role(role).phone(phone).build()
        ));
        persons.add(userId);
    }

    private UUID persistGroup(String name, SectorEntity s) {
        GroupEntity group = GroupEntity.builder().name(name + " " + UUID.randomUUID().toString().substring(0, 4)).sector(s).build();
        groupRepository.persist(group);
        return group.getGroupId();
    }

    /** Sign-ups one minute apart, in the order of this fixture. */
    private LocalDateTime nextSignUp() {
        return LocalDateTime.now(EventEntity.ZONE).minusHours(1).plusMinutes(signUps++);
    }

    private EventRegistrationEntity conducteur(String personId, boolean waiting) {
        LocalDateTime at = nextSignUp();
        return QuarkusTransaction.requiringNew().call(() -> {
            EventRegistrationEntity r = EventRegistrationEntity.conducteur(
                    eventRepository.findById(event), userRepository.findById(UUID.fromString(personId)), waiting);
            r.setSignedUpAt(at);
            r.getJournees().forEach(j -> j.setSignedUpAt(at));
            registrationRepository.persist(r);
            return r;
        });
    }

    private void passager(String personId, String conducteurId) {
        LocalDateTime at = nextSignUp();
        QuarkusTransaction.requiringNew().run(() -> {
            EventRegistrationEntity r = EventRegistrationEntity.passager(
                    registrationRepository.findByEventAndPerson(event, UUID.fromString(conducteurId)).orElseThrow(),
                    userRepository.findById(UUID.fromString(personId)));
            r.setSignedUpAt(at);
            r.getJournees().forEach(j -> j.setSignedUpAt(at));
            registrationRepository.persist(r);
        });
    }

    private String groupName(UUID groupId) {
        return QuarkusTransaction.requiringNew().call(() -> groupRepository.findById(groupId).getName());
    }

    /** Each tab by name, in order, as rows of cells joined by "|". */
    private static Map<String, List<String>> read(byte[] xlsx) throws IOException {
        Map<String, List<String>> tabs = new LinkedHashMap<>();
        try (ReadableWorkbook workbook = new ReadableWorkbook(new ByteArrayInputStream(xlsx))) {
            for (Sheet sheet : workbook.getSheets().toList()) {
                List<String> rows = new ArrayList<>();
                for (Row row : sheet.read()) {
                    List<String> cells = new ArrayList<>();
                    for (int i = 0; i < row.getCellCount(); i++) cells.add(row.getCellText(i));
                    rows.add(String.join("|", cells));
                }
                tabs.put(sheet.getName(), rows);
            }
        }
        return tabs;
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void theBureauDownloadsOneSheetPerJournee() throws IOException {
        List<LocalDate> journees = QuarkusTransaction.requiringNew().call(() -> eventRepository.findById(event).journees());
        // Paul has no conducteur the second Journée
        QuarkusTransaction.requiringNew().run(() -> registrationRepository.findByEventAndPerson(event, UUID.fromString(PAUL_ID))
                .orElseThrow().journee(journees.get(1)).orElseThrow().unpair());

        byte[] xlsx = given().when().get("/{id}/roster/export", event).then().statusCode(200)
                .contentType(RosterSpreadsheet.MEDIA_TYPE)
                .header("Content-Disposition", containsString("attachment"))
                .header("Content-Disposition", containsString("participants-test-balade-"))
                .extract().asByteArray();

        Map<String, List<String>> sheets = read(xlsx);
        DateTimeFormatter name = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH);
        String first = journees.get(0).format(name);
        String second = journees.get(1).format(name);
        String nordName = groupName(nord);
        assertEquals(List.of(first, second), List.copyOf(sheets.keySet()));
        assertEquals(List.of(HEADER,
                "Martin|Anne|06 01 01 01 01|Conducteur|" + nordName + "|Passager : Paul Martin|Participant||01/01/1990|",
                "Martin|Paul|06 02 02 02 02|Passager|" + nordName + "|Derrière Anne Martin|Participant||01/01/1990|",
                "Weber|Claire|06 03 03 03 03|Conducteur|||Participant||01/01/1990|",
                "Klein|Denis|06 04 04 04 04|Conducteur||Passager : Eve Klein|Liste d'attente n°1||01/01/1990|",
                "Klein|Eve|06 05 05 05 05|Passager||Derrière Denis Klein|Liste d'attente n°2||01/01/1990|"), sheets.get(first));
        assertEquals("Martin|Paul|06 02 02 02 02|Passager|" + nordName + "|Sans conducteur|Participant||01/01/1990|", sheets.get(second).get(2));
    }

    @Test
    @TestSecurity(user = "chef", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = CHEF_ID))
    void theExportIsRefusedBelowTheBureau() {
        given().when().get("/{id}/roster/export", event).then().statusCode(403);
    }

    @Test
    void anAnonymousVisiteurCannotExport() {
        given().when().get("/{id}/roster/export", event).then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "bureau", augmentors = DatabaseRoleAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "sub", value = BUREAU_ID))
    void anUnknownEventHasNoExport() {
        given().when().get("/{id}/roster/export", UUID.randomUUID()).then().statusCode(404);
    }
}
