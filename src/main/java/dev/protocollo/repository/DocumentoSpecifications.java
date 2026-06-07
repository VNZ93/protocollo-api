package dev.protocollo.repository;

import dev.protocollo.domain.Documento;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Costruisce dinamicamente le query di ricerca dei documenti a partire da un
 * {@link FiltroDocumenti}.
 *
 * Le {@code Specification} di Spring Data permettono di comporre i criteri in
 * modo type-safe: si aggiungono solo i predicati relativi ai filtri valorizzati,
 * evitando di scrivere tante query diverse per ogni combinazione.
 */
public final class DocumentoSpecifications {

    private DocumentoSpecifications() {
        // Classe di utilita: non istanziabile
    }

    /**
     * Restituisce una Specification che combina in AND tutti i filtri presenti.
     */
    public static Specification<Documento> daFiltro(FiltroDocumenti filtro) {
        return (root, query, cb) -> {
            List<Predicate> predicati = new ArrayList<>();

            if (filtro.stato() != null) {
                predicati.add(cb.equal(root.get("stato"), filtro.stato()));
            }
            if (StringUtils.hasText(filtro.proprietario())) {
                predicati.add(cb.equal(root.get("proprietario"), filtro.proprietario()));
            }
            if (StringUtils.hasText(filtro.testo())) {
                String pattern = "%" + filtro.testo().toLowerCase() + "%";
                predicati.add(cb.like(cb.lower(root.get("titolo")), pattern));
            }
            if (filtro.creatoDa() != null) {
                predicati.add(cb.greaterThanOrEqualTo(root.get("dataCreazione"), filtro.creatoDa()));
            }
            if (filtro.creatoA() != null) {
                predicati.add(cb.lessThanOrEqualTo(root.get("dataCreazione"), filtro.creatoA()));
            }

            // Se nessun filtro e valorizzato, la lista e vuota: nessun vincolo (restituisce tutto)
            return cb.and(predicati.toArray(new Predicate[0]));
        };
    }
}
