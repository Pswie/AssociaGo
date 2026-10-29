package com.associago.expense;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lettura delle fatture elettroniche.
 *
 * <p>Il caso più importante di questa classe non è quello felice ma
 * {@link #xxeIsRefused()}: sono file che arrivano da fuori, e un parser XML
 * configurato male è una porta aperta sul server.
 */
@DisplayName("Fattura elettronica XML")
class FatturaXmlParserTest {

    private static byte[] bytes(String xml) {
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private static final String FATTURA_VALIDA = """
        <?xml version="1.0" encoding="UTF-8"?>
        <p:FatturaElettronica xmlns:p="http://ivaservizi.agenziaentrate.gov.it/docs/xsd/fatture/v1.2" versione="FPR12">
          <FatturaElettronicaHeader>
            <CedentePrestatore>
              <DatiAnagrafici>
                <IdFiscaleIVA>
                  <IdPaese>IT</IdPaese>
                  <IdCodice>01234567890</IdCodice>
                </IdFiscaleIVA>
                <CodiceFiscale>01234567890</CodiceFiscale>
                <Anagrafica>
                  <Denominazione>Cartoleria Vesuvio srl</Denominazione>
                </Anagrafica>
              </DatiAnagrafici>
            </CedentePrestatore>
          </FatturaElettronicaHeader>
          <FatturaElettronicaBody>
            <DatiGenerali>
              <DatiGeneraliDocumento>
                <TipoDocumento>TD01</TipoDocumento>
                <Divisa>EUR</Divisa>
                <Data>2026-07-10</Data>
                <Numero>2026/145</Numero>
                <ImportoTotaleDocumento>244.00</ImportoTotaleDocumento>
              </DatiGeneraliDocumento>
            </DatiGenerali>
            <DatiBeniServizi>
              <DatiRiepilogo>
                <AliquotaIVA>22.00</AliquotaIVA>
                <ImponibileImporto>200.00</ImponibileImporto>
                <Imposta>44.00</Imposta>
              </DatiRiepilogo>
            </DatiBeniServizi>
            <DatiPagamento>
              <DettaglioPagamento>
                <DataScadenzaPagamento>2026-08-09</DataScadenzaPagamento>
              </DettaglioPagamento>
            </DatiPagamento>
          </FatturaElettronicaBody>
        </p:FatturaElettronica>
        """;

    @Test
    @DisplayName("legge fornitore, numero, data, importo, IVA e scadenza")
    void parsesValidInvoice() {
        ExpenseDocument document = FatturaXmlParser.parse(bytes(FATTURA_VALIDA), 1L);

        assertThat(document.getSupplierName()).isEqualTo("Cartoleria Vesuvio srl");
        assertThat(document.getSupplierVat()).isEqualTo("01234567890");
        assertThat(document.getNumber()).isEqualTo("2026/145");
        assertThat(document.getDocumentDate()).isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(document.getTotalAmount()).isEqualByComparingTo("244.00");
        assertThat(document.getVatAmount()).isEqualByComparingTo("44.00");
        assertThat(document.getDueDate()).isEqualTo(LocalDate.of(2026, 8, 9));
        assertThat(document.getStatus()).isEqualTo(ExpenseDocument.DA_PAGARE);
    }

    @Test
    @DisplayName("l'impronta è stabile e distingue file diversi")
    void hashIsStableAndDistinct() {
        String hashA = FatturaXmlParser.parse(bytes(FATTURA_VALIDA), 1L).getSourceHash();
        String hashB = FatturaXmlParser.parse(bytes(FATTURA_VALIDA), 1L).getSourceHash();
        String hashC = FatturaXmlParser
                .parse(bytes(FATTURA_VALIDA.replace("2026/145", "2026/146")), 1L)
                .getSourceHash();

        assertThat(hashA).isEqualTo(hashB).hasSize(64);
        assertThat(hashC).isNotEqualTo(hashA);
    }

    @Test
    @DisplayName("un prefisso di namespace diverso non cambia nulla")
    void namespacePrefixIsIrrelevant() {
        String altPrefix = FATTURA_VALIDA
                .replace("p:FatturaElettronica", "ns2:FatturaElettronica")
                .replace("xmlns:p=", "xmlns:ns2=");

        assertThat(FatturaXmlParser.parse(bytes(altPrefix), 1L).getSupplierName())
                .isEqualTo("Cartoleria Vesuvio srl");
    }

    @Test
    @DisplayName("il fornitore persona fisica ha nome e cognome, non denominazione")
    void individualSupplier() {
        String persona = FATTURA_VALIDA.replace(
                "<Denominazione>Cartoleria Vesuvio srl</Denominazione>",
                "<Nome>Mario</Nome><Cognome>Esposito</Cognome>");

        assertThat(FatturaXmlParser.parse(bytes(persona), 1L).getSupplierName())
                .isEqualTo("Mario Esposito");
    }

    @Test
    @DisplayName("senza ImportoTotaleDocumento somma imponibile e imposta")
    void fallsBackToRiepilogo() {
        String senzaTotale = FATTURA_VALIDA
                .replace("<ImportoTotaleDocumento>244.00</ImportoTotaleDocumento>", "");

        assertThat(FatturaXmlParser.parse(bytes(senzaTotale), 1L).getTotalAmount())
                .isEqualByComparingTo("244.00");
    }

    @Test
    @DisplayName("un XML con entità esterne viene rifiutato, non risolto")
    void xxeIsRefused() {
        // Con un parser configurato in modo predefinito, &xxe; verrebbe
        // espansa leggendo un file del server. Qui la dichiarazione DOCTYPE è
        // vietata a monte, quindi il file viene respinto prima di arrivarci.
        String malevolo = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE foo [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
            <FatturaElettronica>
              <CedentePrestatore><DatiAnagrafici><Anagrafica>
                <Denominazione>&xxe;</Denominazione>
              </Anagrafica></DatiAnagrafici></CedentePrestatore>
            </FatturaElettronica>
            """;

        assertThatThrownBy(() -> FatturaXmlParser.parse(bytes(malevolo), 1L))
                .isInstanceOf(FatturaXmlParser.InvalidFatturaException.class);
    }

    @Test
    @DisplayName("un file firmato .p7m viene riconosciuto e spiegato")
    void signedFileIsExplained() {
        byte[] p7m = new byte[] { 0x30, (byte) 0x82, 0x0A, 0x1B, 0x06, 0x09 };

        assertThatThrownBy(() -> FatturaXmlParser.parse(p7m, 1L))
                .isInstanceOf(FatturaXmlParser.InvalidFatturaException.class)
                .hasMessageContaining("p7m");
    }

    @Test
    @DisplayName("un XML che non è una fattura viene respinto con un motivo")
    void nonInvoiceXmlIsRefused() {
        assertThatThrownBy(() -> FatturaXmlParser.parse(bytes("<qualcosa><altro/></qualcosa>"), 1L))
                .isInstanceOf(FatturaXmlParser.InvalidFatturaException.class)
                .hasMessageContaining("CedentePrestatore");
    }

    @Test
    @DisplayName("un file vuoto non manda in errore oscuro")
    void emptyFile() {
        assertThatThrownBy(() -> FatturaXmlParser.parse(new byte[0], 1L))
                .isInstanceOf(FatturaXmlParser.InvalidFatturaException.class)
                .hasMessageContaining("vuoto");
    }
}
