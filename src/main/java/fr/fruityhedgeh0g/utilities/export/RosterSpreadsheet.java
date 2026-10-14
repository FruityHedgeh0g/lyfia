package fr.fruityhedgeh0g.utilities.export;

import fr.fruityhedgeh0g.dtos.eventDtos.RosterDto;
import fr.fruityhedgeh0g.dtos.eventDtos.RosterEntryDto;
import fr.fruityhedgeh0g.dtos.userDtos.NestedUserDto;
import fr.fruityhedgeh0g.enums.PosteEnum;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * An Event's roster as an .xlsx file: one sheet per Journée (ADR 0011), the list of the day on the ground. Each row
 * gives name, phone, Poste, Groupe and who the person rides with that Journée (a passager's conducteur, a
 * conducteur's passager), Participants first in sign-up order, then the Liste d'attente in its order.
 */
public final class RosterSpreadsheet {

    public static final String MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    static final String LISTE_D_ATTENTE = "Liste d'attente";
    static final String SANS_CONDUCTEUR = "Sans conducteur";
    private static final List<String> HEADER = List.of("Nom", "Prénom", "Téléphone", "Poste", "Groupe", "Avec", "Place");
    private static final DateTimeFormatter SHEET_NAME = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH);

    private RosterSpreadsheet() {
    }

    /** One sheet per Journée's roster, named after the Journée (e.g. « samedi 10 mai »). */
    public static byte[] of(List<RosterDto> journees) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Workbook workbook = new Workbook(out, "lyfia", "1.0");
            for (RosterDto roster : journees) {
                Worksheet sheet = workbook.newWorksheet(sheetName(roster.journee()));
                for (int c = 0; c < HEADER.size(); c++) sheet.value(0, c, HEADER.get(c));
                sheet.range(0, 0, 0, HEADER.size() - 1).style().bold().set();
                int row = 1;
                for (RosterEntryDto person : roster.participants()) write(sheet, row++, person, "Participant");
                for (int i = 0; i < roster.waiting().size(); i++)
                    write(sheet, row++, roster.waiting().get(i), LISTE_D_ATTENTE + " n°" + (i + 1));
            }
            workbook.finish();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the roster spreadsheet.", e);
        }
        return out.toByteArray();
    }

    /** A Journée's sheet name: its weekday and date, which Excel accepts as a tab name. */
    static String sheetName(LocalDate journee) {
        return journee.format(SHEET_NAME);
    }

    private static void write(Worksheet sheet, int row, RosterEntryDto person, String place) {
        sheet.value(row, 0, person.lastName());
        sheet.value(row, 1, person.firstName());
        sheet.value(row, 2, Objects.requireNonNullElse(person.phone(), ""));
        sheet.value(row, 3, posteLabel(person.poste()));
        sheet.value(row, 4, person.group() == null ? "" : person.group().name());
        sheet.value(row, 5, withWhom(person));
        sheet.value(row, 6, place);
    }

    /** Who rides with the person that Journée; a passager without a conducteur stands out. */
    private static String withWhom(RosterEntryDto person) {
        if (person.poste() == PosteEnum.PASSAGER)
            return person.conducteur() == null ? SANS_CONDUCTEUR : "Derrière " + name(person.conducteur());
        return person.passager() == null ? "" : "Passager : " + name(person.passager());
    }

    private static String name(NestedUserDto person) {
        return person.firstName() + " " + person.lastName();
    }

    private static String posteLabel(PosteEnum poste) {
        return switch (poste) {
            case CONDUCTEUR -> "Conducteur";
            case PASSAGER -> "Passager";
            case CHAUFFEUR_CAMIONNETTE -> "Chauffeur camionnette";
            case POINT_FIXE -> "Point fixe";
            case SERVICE -> "Service";
        };
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
}
