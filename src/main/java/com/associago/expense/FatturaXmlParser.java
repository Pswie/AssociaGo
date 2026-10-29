package com.associago.expense;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Lettura di una fattura elettronica italiana (FatturaPA) in
 * {@link ExpenseDocument}.
 *
 * <p><strong>Sicurezza.</strong> Questi file arrivano da fuori. Un parser XML
 * con le impostazioni predefinite di Java accetta le dichiarazioni DOCTYPE e
 * risolve le entità esterne: basta una fattura confezionata ad arte per farsi
 * leggere file del server o aprire connessioni di rete (XXE). Qui le
 * dichiarazioni DOCTYPE sono vietate del tutto e l'accesso a DTD e schemi
 * esterni è azzerato.
 *
 * <p>Il tracciato è XSD 1.2.x. I nomi degli elementi vengono cercati per nome
 * locale, ignorando il prefisso di namespace, perché nella pratica gli
 * emittenti usano prefissi diversi (`p:`, `ns2:`, nessuno) per lo stesso
 * tracciato.
 *
 * <p>Non gestisce i file `.p7m`, cioè le fatture con firma digitale CAdES:
 * vanno prima estratte, ed è un problema diverso, di crittografia, non di
 * lettura del tracciato.
 *
 * @since 0.2.0
 */
public final class FatturaXmlParser {

    private static final Logger logger = LoggerFactory.getLogger(FatturaXmlParser.class);

    private FatturaXmlParser() {
    }

    /** Errore di lettura: file non valido o non riconducibile a una fattura. */
    public static class InvalidFatturaException extends RuntimeException {
        public InvalidFatturaException(String message) {
            super(message);
        }

        public InvalidFatturaException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * @param xml contenuto del file, non ancora decodificato
     * @param associationId associazione a cui attribuire il documento
     */
    public static ExpenseDocument parse(byte[] xml, Long associationId) {
        if (xml == null || xml.length == 0) {
            throw new InvalidFatturaException("Il file è vuoto.");
        }
        if (looksLikeP7m(xml)) {
            throw new InvalidFatturaException(
                    "Il file sembra firmato digitalmente (.p7m). Va prima estratto "
                            + "il contenuto XML: la verifica della firma è un passaggio a sé.");
        }

        Document document = parseSecurely(xml);

        ExpenseDocument expense = new ExpenseDocument();
        expense.setAssociationId(associationId);
        expense.setDocumentType("fattura");
        expense.setSourceHash(sha256(xml));

        Element supplier = firstElement(document, "CedentePrestatore")
                .orElseThrow(() -> new InvalidFatturaException(
                        "Manca il blocco CedentePrestatore: non è una fattura elettronica."));

        expense.setSupplierName(readSupplierName(supplier));
        expense.setSupplierVat(textIn(supplier, "IdCodice").orElse(null));
        expense.setSupplierTaxCode(textIn(supplier, "CodiceFiscale").orElse(null));

        Element general = firstElement(document, "DatiGeneraliDocumento")
                .orElseThrow(() -> new InvalidFatturaException(
                        "Manca il blocco DatiGeneraliDocumento."));

        expense.setNumber(textIn(general, "Numero").orElse(null));
        expense.setDocumentDate(textIn(general, "Data")
                .map(FatturaXmlParser::parseDate)
                .orElseThrow(() -> new InvalidFatturaException(
                        "Manca la data del documento.")));
        expense.setTotalAmount(textIn(general, "ImportoTotaleDocumento")
                .map(FatturaXmlParser::parseAmount)
                .orElseGet(() -> sumRiepilogo(document)));

        firstElement(document, "DatiRiepilogo")
                .flatMap(riepilogo -> textIn(riepilogo, "Imposta"))
                .map(FatturaXmlParser::parseAmount)
                .ifPresent(expense::setVatAmount);

        firstElement(document, "DettaglioPagamento")
                .flatMap(pagamento -> textIn(pagamento, "DataScadenzaPagamento"))
                .map(FatturaXmlParser::parseDate)
                .ifPresent(expense::setDueDate);

        if (expense.getTotalAmount() == null) {
            throw new InvalidFatturaException(
                    "Impossibile determinare l'importo totale del documento.");
        }

        return expense;
    }

    /**
     * Parser DOM con le difese contro XXE attive.
     *
     * <p>Vietare il DOCTYPE è la difesa più solida: senza dichiarazione di tipo
     * non esistono entità da espandere, e nessuna delle varianti dell'attacco
     * ha appiglio. Le altre impostazioni restano come rete di sicurezza.
     */
    private static Document parseSecurely(byte[] xml) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(true);

            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xml));
        } catch (ParserConfigurationException e) {
            throw new InvalidFatturaException(
                    "Impossibile configurare il lettore XML in modo sicuro.", e);
        } catch (SAXException e) {
            throw new InvalidFatturaException("Il file non è un XML valido: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new InvalidFatturaException("Impossibile leggere il file.", e);
        }
    }

    /**
     * Ragione sociale, oppure nome e cognome per i fornitori persona fisica
     * (il tracciato prevede l'uno o gli altri, mai entrambi).
     */
    private static String readSupplierName(Element supplier) {
        Optional<String> denominazione = textIn(supplier, "Denominazione");
        if (denominazione.isPresent()) {
            return denominazione.get();
        }
        String nome = textIn(supplier, "Nome").orElse("");
        String cognome = textIn(supplier, "Cognome").orElse("");
        String full = (nome + " " + cognome).trim();
        if (full.isEmpty()) {
            throw new InvalidFatturaException("Manca la denominazione del fornitore.");
        }
        return full;
    }

    /** Fallback quando manca ImportoTotaleDocumento: somma degli imponibili con imposta. */
    private static BigDecimal sumRiepilogo(Document document) {
        NodeList riepiloghi = document.getElementsByTagNameNS("*", "DatiRiepilogo");
        if (riepiloghi.getLength() == 0) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < riepiloghi.getLength(); i++) {
            Element riepilogo = (Element) riepiloghi.item(i);
            BigDecimal imponibile = textIn(riepilogo, "ImponibileImporto")
                    .map(FatturaXmlParser::parseAmount).orElse(BigDecimal.ZERO);
            BigDecimal imposta = textIn(riepilogo, "Imposta")
                    .map(FatturaXmlParser::parseAmount).orElse(BigDecimal.ZERO);
            total = total.add(imponibile).add(imposta);
        }
        return total;
    }

    private static Optional<Element> firstElement(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            return Optional.empty();
        }
        return Optional.of((Element) nodes.item(0));
    }

    private static Optional<String> textIn(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            return Optional.empty();
        }
        Node node = nodes.item(0);
        String text = node.getTextContent();
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(text.trim());
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidFatturaException("Data non valida nel documento: " + value, e);
        }
    }

    private static BigDecimal parseAmount(String value) {
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new InvalidFatturaException("Importo non valido nel documento: " + value, e);
        }
    }

    /** I file firmati CAdES cominciano con una struttura DER, non con '<'. */
    private static boolean looksLikeP7m(byte[] content) {
        for (byte b : content) {
            if (b == ' ' || b == '\n' || b == '\r' || b == '\t') {
                continue;
            }
            return b != '<' && b != (byte) 0xEF; // 0xEF: BOM UTF-8
        }
        return false;
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 è garantito da ogni JVM: se manca, l'ambiente è rotto.
            logger.error("SHA-256 non disponibile", e);
            throw new IllegalStateException(e);
        }
    }

    /** Utile nei test e nei log: il tracciato è sempre UTF-8. */
    static String asString(byte[] xml) {
        return new String(xml, StandardCharsets.UTF_8);
    }
}
