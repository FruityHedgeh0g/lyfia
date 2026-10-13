package fr.fruityhedgeh0g.repositories;

import fr.fruityhedgeh0g.entities.UserEntity;
import fr.fruityhedgeh0g.enums.BureauFunctionEnum;
import fr.fruityhedgeh0g.enums.RoleEnum;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class UserRepository implements PanacheRepositoryBase<UserEntity, UUID> {

    /** The Président of a Secteur (at most one). */
    public List<UserEntity> findPresidentsOf(UUID sectorId){
        return list("bureauFunction = ?1 and sector.sectorId = ?2", BureauFunctionEnum.PRESIDENT, sectorId);
    }

    /** A Secteur's Inscrits: its people, and the Bénévoles with a sign-up for one of its Events (ADR 0004). */
    public List<UserEntity> listInscritsOf(UUID sectorId){
        return list("select u from UserEntity u left join u.sector s where s.sectorId = ?1 or (u.role = ?2 and exists "
                + "(select r from EventRegistrationEntity r where r.person = u and r.event.sector.sectorId = ?1))",
                sectorId, RoleEnum.BENEVOLE);
    }

    /** true once the person has signed up for one of the Secteur's Events, past ones included. */
    public boolean rodeWith(UUID personId, UUID sectorId){
        return getEntityManager().createQuery("select count(r) from EventRegistrationEntity r "
                        + "where r.person.userId = ?1 and r.event.sector.sectorId = ?2", Long.class)
                .setParameter(1, personId).setParameter(2, sectorId).getSingleResult() > 0;
    }

    public boolean existsById(UUID userId){
        return count("userId", userId) > 0;
    }

//    public Optional<UserEntity> findByName(String token) {
//        return Optional.ofNullable(find("userId",token)
//                .firstResult());
//    }
}
