package dev.protocollo.messaging;

import dev.protocollo.domain.StatoDocumento;

/**
 * Evento in ingresso, prodotto da un indice/sistema esterno, che segnala un
 * aggiornamento su una risorsa (documento) gia protocollata.
 *
 * Viene consumato da {@link IndiceConsumer} per allineare lo stato locale del
 * documento a quanto comunicato dall'indice esterno.
 *
 * @param idDocumento id del documento a cui si riferisce l'aggiornamento
 * @param nuovoStato  nuovo stato da applicare (facoltativo: se null non cambia)
 * @param origine     nome del sistema che ha generato l'evento (per tracciamento)
 */
public record IndiceAggiornamentoEvent(
        Long idDocumento,
        StatoDocumento nuovoStato,
        String origine) {
}
