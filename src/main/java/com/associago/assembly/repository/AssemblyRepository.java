package com.associago.assembly.repository;

import com.associago.assembly.Assembly;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AssemblyRepository extends CrudRepository<Assembly, Long> {
    List<Assembly> findByAssociationId(Long associationId);

    /**
     * Le assemblee di un esercizio.
     *
     * <p>Un'assemblea è un fatto <strong>puntuale</strong>: si tiene in un
     * giorno. Il verbale che ne resta appartiene a quell'anno e a nessun altro.
     *
     * <p>Una data assente la fa comparire comunque: una riga da sistemare è
     * meglio di una riga invisibile in ogni anno.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT a FROM Assembly a WHERE a.associationId = :associationId "
            + "AND (a.date IS NULL OR YEAR(a.date) = :anno)")
    List<Assembly> findByAssociationIdAndAnno(
            @org.springframework.data.repository.query.Param("associationId") Long associationId,
            @org.springframework.data.repository.query.Param("anno") int anno);
}
