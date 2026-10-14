package fr.fruityhedgeh0g.utilities;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class NameMatchingTest {

    record Person(String firstName, String lastName) {
    }

    private static NameMatching.Outcome<Person> find(String typed, Person... people) {
        return NameMatching.find(typed, List.of(people), Person::firstName, Person::lastName);
    }

    @Test
    void ignoresCaseAccentsPunctuationAndOrder() {
        Person helene = new Person("Hélène", "Müller-Weber");
        assertEquals(new NameMatching.Outcome.Found<>(helene), find("helene muller weber", helene));
        assertEquals(new NameMatching.Outcome.Found<>(helene), find("MULLER WEBER Hélène", helene));
    }

    @Test
    void toleratesATypo() {
        Person marc = new Person("Marc", "Weber");
        assertEquals(new NameMatching.Outcome.Found<>(marc), find("Marc Webre", marc, new Person("Paul", "Martin")));
    }

    @Test
    void findsNobodyFromAPartOrAnotherName() {
        Person marc = new Person("Marc", "Weber");
        assertInstanceOf(NameMatching.Outcome.NotFound.class, find("Marc", marc));
        assertInstanceOf(NameMatching.Outcome.NotFound.class, find("Paul Weber", marc));
        assertInstanceOf(NameMatching.Outcome.NotFound.class, find("  ", marc));
    }

    @Test
    void refusesToChooseBetweenTwoPeopleAboutAsAlike() {
        assertInstanceOf(NameMatching.Outcome.Ambiguous.class,
                find("Marc Weber", new Person("Marc", "Weber"), new Person("Marc", "Weber")));
        assertInstanceOf(NameMatching.Outcome.Ambiguous.class,
                find("Jeane Klein", new Person("Jean", "Klein"), new Person("Jeanne", "Klein")));
    }

    @Test
    void picksTheOneBearingTheVeryNameTyped() {
        Person weber = new Person("Marc", "Weber");
        assertEquals(new NameMatching.Outcome.Found<>(weber), find("Marc Weber", weber, new Person("Marc", "Webern")));
    }
}
