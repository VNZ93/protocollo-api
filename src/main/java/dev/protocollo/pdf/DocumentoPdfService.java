package dev.protocollo.pdf;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Genera il PDF di accreditamento a partire da un template XHTML con segnaposto.
 *
 * Il flusso e: carico il template, sostituisco i segnaposto ${...} con i dati
 * del documento/utente, poi lascio che Flying Saucer renda l'XHTML in PDF.
 * Tenere il layout in un template separato (non nel codice Java) e una buona
 * pratica: la grafica si modifica senza ricompilare.
 */
@Service
public class DocumentoPdfService {

    private static final String PERCORSO_TEMPLATE = "templates/documento-accreditamento.html";

    private static final DateTimeFormatter FORMATO_DATA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    /** Il template viene letto una sola volta e tenuto in memoria. */
    private final String template;

    public DocumentoPdfService() {
        this.template = caricaTemplate();
    }

    /**
     * Costruisce il PDF a partire dai dati di accreditamento.
     *
     * @return i byte del PDF generato
     */
    public byte[] genera(DatiAccreditamento dati) {
        String html = riempiTemplate(dati);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Errore nella generazione del PDF", e);
        }
    }

    /** Sostituisce i segnaposto del template con i valori (sempre dopo escaping). */
    private String riempiTemplate(DatiAccreditamento dati) {
        return template
                .replace("${titolo}", escape(dati.titolo()))
                .replace("${numeroProtocollo}", escape(dati.numeroProtocollo()))
                .replace("${nomeCompleto}", escape(dati.nomeCompleto()))
                .replace("${email}", escape(dati.email()))
                .replace("${proprietario}", escape(dati.proprietario()))
                .replace("${dataCreazione}", escape(FORMATO_DATA.format(dati.dataCreazione())))
                .replace("${contenuto}", escape(dati.contenuto()))
                .replace("${servizi}", costruisciVociServizi(dati.servizi()));
    }

    /** Trasforma la lista dei servizi in una sequenza di elementi {@code <li>}. */
    private String costruisciVociServizi(List<String> servizi) {
        if (servizi == null || servizi.isEmpty()) {
            return "<li>Nessun servizio</li>";
        }
        StringBuilder voci = new StringBuilder();
        for (String servizio : servizi) {
            voci.append("<li>").append(escape(servizio)).append("</li>");
        }
        return voci.toString();
    }

    private String caricaTemplate() {
        try {
            byte[] byteTemplate = new ClassPathResource(PERCORSO_TEMPLATE)
                    .getInputStream().readAllBytes();
            return new String(byteTemplate, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Template PDF non trovato: " + PERCORSO_TEMPLATE, e);
        }
    }

    /**
     * Escape dei caratteri speciali XML/HTML: evita che un valore (es. un titolo
     * con "&" o "<") rompa l'XHTML e quindi la generazione del PDF.
     */
    private String escape(String testo) {
        if (testo == null) {
            return "";
        }
        return testo
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
