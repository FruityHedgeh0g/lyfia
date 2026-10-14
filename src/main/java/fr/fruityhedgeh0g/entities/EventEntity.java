package fr.fruityhedgeh0g.entities;

import fr.fruityhedgeh0g.enums.EventStatusEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "events")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class EventEntity extends AuditTemplate {

    @Id
    @Column(name = "event_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID eventId;

    /** Manual status (Planification, Ouvert, Complet, Annulé); see {@link #currentStatus()}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EventStatusEnum status = EventStatusEnum.PLANIFICATION;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sector_id")
    private SectorEntity sector;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "startDateTime", nullable = false)
    private LocalDateTime startDateTime;

    @Column(name = "endDateTime")
    private LocalDateTime endDateTime;

    @Column(name = "latitude")
    private String latitude;

    @Column(name = "longitude")
    private String longitude;

    @Column(name = "address")
    private String address;

    @Column(name = "city")
    private String city;

    @Column(name = "country")
    private String country;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "address_complement")
    private String addressComplement;

    @Column(name = "image_url")
    private String imageUrl;

    /** Optional overall maximum of Participants; beyond it, sign-ups go onto the Liste d'attente. */
    @Column(name = "max_participants")
    private Integer maxParticipants;

    /** Optional maximum per Groupe (by id) at this Event; beyond it, Demandes wait as the Groupe's Liste d'attente. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "event_group_maximums", joinColumns = @JoinColumn(name = "event_id"))
    @MapKeyColumn(name = "group_id")
    @Column(name = "maximum", nullable = false)
    private Map<UUID, Integer> groupMaximums = new HashMap<>();

    /** true when the Groupe belongs to this Event's Secteur. */
    public boolean isOfSectorOf(GroupEntity group) {
        return sector != null && group.getSector() != null && sector.getSectorId().equals(group.getSector().getSectorId());
    }

    /** The Groupe's maximum at this Event, or null when unlimited. */
    public Integer maximumOf(GroupEntity group) {
        return groupMaximums.get(group.getGroupId());
    }

    /** Sets the Groupe's maximum at this Event; null, 0 or less removes it. */
    public void setMaximumOf(GroupEntity group, Integer maximum) {
        if (maximum == null || maximum <= 0) groupMaximums.remove(group.getGroupId());
        else groupMaximums.put(group.getGroupId(), maximum);
    }

    /** Dates are entered and read in the association's local time, whatever the server's zone. */
    public static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    /** true while the Event's Secteur is fermé. */
    public boolean isInClosedSector() {
        return sector != null && sector.isClosed();
    }

    /** Its Journées: each calendar date from its start to its end (ADR 0011). */
    public List<LocalDate> journees() {
        if (startDateTime == null) return List.of();
        LocalDate last = endDateTime == null ? startDateTime.toLocalDate() : endDateTime.toLocalDate();
        return startDateTime.toLocalDate().datesUntil(last.plusDays(1)).toList();
    }

    /** true when that date is one of its Journées. */
    public boolean hasJournee(LocalDate journee) {
        return journees().contains(journee);
    }

    /** Status as of now: an Ouvert or Complet Event reads En cours, then Archivé, from its dates. */
    public EventStatusEnum currentStatus() {
        return status.at(startDateTime, endDateTime, LocalDateTime.now(ZONE));
    }
}
