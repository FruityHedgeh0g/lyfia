package fr.fruityhedgeh0g.utilities.export;

import fr.fruityhedgeh0g.dtos.eventDtos.GroupRosterDto;
import fr.fruityhedgeh0g.dtos.eventDtos.RosterDto;
import fr.fruityhedgeh0g.dtos.eventDtos.RosterEntryDto;
import fr.fruityhedgeh0g.dtos.groupDtos.GroupRefDto;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An Event's roster as an .xlsx file: one tab per Groupe of its Secteur with the Participants riding
 * with it, one for Participants without a Groupe, and one for the Liste d'attente, whose people are
 * left out of the other tabs. Each row gives name, phone and conducteur/passager (with a passager's
 * conducteur), in sign-up order.
 */
public final class RosterSpreadsheet {

    public static final String MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    static final String WITHOUT_GROUPE = "Sans groupe";
    static final String LISTE_D_ATTENTE = "Liste d'attente";
    private static final List<String> HEADER = List.of("Nom", "Prénom", "Téléphone", "Conducteur / passager", "Conducteur");
    /** Excel caps tab names at 31 characters and forbids these ones. */
    private static final int MAX_TAB_NAME = 31;
    private static final String FORBIDDEN_IN_TAB_NAME = "[\\[\\]:*?/\\\\]";

    private RosterSpreadsheet() {
    }

    public static byte[] of(RosterDto roster) {
        Map<String, List<RosterEntryDto>> tabs = new LinkedHashMap<>();
        // The fixed tabs keep their names: a Groupe called like one of them gets a suffix instead
        Set<String> names = new HashSet<>(List.of(WITHOUT_GROUPE.toLowerCase(), LISTE_D_ATTENTE.toLowerCase()));
        roster.groups().stream().map(GroupRosterDto::group).forEach(group ->
                tabs.put(tabName(group.name(), names), participantsOf(roster, group)));
        // A Participant placed in a Groupe of another Secteur still gets a tab
        roster.participants().stream().map(RosterEntryDto::group).filter(Objects::nonNull).distinct()
                .filter(group -> roster.groups().stream().noneMatch(g -> g.group().groupId().equals(group.groupId())))
                .forEach(group -> tabs.put(tabName(group.name(), names), participantsOf(roster, group)));
        tabs.put(WITHOUT_GROUPE, roster.participants().stream().filter(p -> p.group() == null).toList());
        tabs.put(LISTE_D_ATTENTE, roster.waiting());
        return write(tabs);
    }

    /** A plain-ASCII file name for the Event's export, e.g. {@code participants-balade-du-printemps-2026-05-10.xlsx}. */
    public static String fileName(String eventName, LocalDate startDate) {
        String slug = Normalizer.normalize(eventName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("(^-|-$)", "")
                .toLowerCase();
        return "participants-" + (slug.isEmpty() ? "evenement" : slug) + "-" + startDate + ".xlsx";
    }

    private static List<RosterEntryDto> participantsOf(RosterDto roster, GroupRefDto group) {
        return roster.participants().stream()
                .filter(p -> p.group() != null && p.group().groupId().equals(group.groupId()))
                .toList();
    }

    /** A valid, unique tab name. */
    private static String tabName(String wanted, Set<String> taken) {
        String base = wanted.replaceAll(FORBIDDEN_IN_TAB_NAME, " ").strip();
        if (base.isEmpty()) base = "Groupe";
        base = truncate(base, MAX_TAB_NAME);
        String name = base;
        for (int i = 2; taken.contains(name.toLowerCase()); i++) {
            String suffix = " (" + i + ")";
            name = truncate(base, MAX_TAB_NAME - suffix.length()) + suffix;
        }
        taken.add(name.toLowerCase());
        return name;
    }

    private static String truncate(String text, int length) {
        return text.length() <= length ? text : text.substring(0, length).strip();
    }

    private static byte[] write(Map<String, List<RosterEntryDto>> tabs) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Workbook workbook = new Workbook(out, "lyfia", "1.0");
            tabs.forEach((name, people) -> {
                Worksheet sheet = workbook.newWorksheet(name);
                for (int c = 0; c < HEADER.size(); c++) sheet.value(0, c, HEADER.get(c));
                sheet.range(0, 0, 0, HEADER.size() - 1).style().bold().set();
                for (int r = 0; r < people.size(); r++) {
                    RosterEntryDto person = people.get(r);
                    sheet.value(r + 1, 0, person.lastName());
                    sheet.value(r + 1, 1, person.firstName());
                    sheet.value(r + 1, 2, Objects.requireNonNullElse(person.phone(), ""));
                    sheet.value(r + 1, 3, person.mode().id());
                    if (person.conducteur() != null)
                        sheet.value(r + 1, 4, person.conducteur().firstName() + " " + person.conducteur().lastName());
                }
            });
            workbook.finish();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the roster spreadsheet.", e);
        }
        return out.toByteArray();
    }
}
