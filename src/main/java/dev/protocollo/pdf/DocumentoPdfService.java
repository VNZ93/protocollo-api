package dev.protocollo.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfWriter;
import dev.protocollo.domain.Documento;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Genera la rappresentazione PDF di un documento, usando la libreria OpenPDF.
 *
 * Restituisce i byte del PDF: e poi il chiamante (il service) a deciderne il
 * salvataggio tramite {@code DocumentStorage}, mantenendo separate la
 * generazione del file e la sua persistenza.
 */
@Service
public class DocumentoPdfService {

    private static final DateTimeFormatter FORMATO_DATA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private static final Font FONT_TITOLO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font FONT_ETICHETTA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    private static final Font FONT_TESTO = FontFactory.getFont(FontFactory.HELVETICA, 11);

    /**
     * Costruisce il PDF a partire dall'entita documento.
     *
     * @return i byte del PDF generato
     */
    public byte[] genera(Documento documento) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document pdf = new Document();
        try {
            PdfWriter.getInstance(pdf, output);
            pdf.open();

            // Intestazione
            Paragraph titolo = new Paragraph(documento.getTitolo(), FONT_TITOLO);
            titolo.setSpacingAfter(20f);
            pdf.add(titolo);

            // Metadati di protocollo
            pdf.add(riga("Numero di protocollo: ", valore(documento.getNumeroProtocollo())));
            pdf.add(riga("Stato: ", documento.getStato().name()));
            pdf.add(riga("Proprietario: ", documento.getProprietario()));
            pdf.add(riga("Data creazione: ", FORMATO_DATA.format(documento.getDataCreazione())));

            // Corpo
            Paragraph contenuto = new Paragraph(valore(documento.getContenuto()), FONT_TESTO);
            contenuto.setSpacingBefore(20f);
            pdf.add(contenuto);

            pdf.close();
            return output.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Errore nella generazione del PDF", e);
        }
    }

    /** Riga "etichetta + valore" con stili diversi sulle due parti. */
    private Paragraph riga(String etichetta, String valore) {
        Paragraph paragrafo = new Paragraph();
        paragrafo.add(new Phrase(etichetta, FONT_ETICHETTA));
        paragrafo.add(new Phrase(valore, FONT_TESTO));
        paragrafo.setAlignment(Element.ALIGN_LEFT);
        return paragrafo;
    }

    private String valore(String testo) {
        return testo != null ? testo : "";
    }
}
