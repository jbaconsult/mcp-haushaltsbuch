package de.jbaconsult.haushaltsbuch.kern;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Liest ISO 20022 CAMT.052 als reine Funktion über Text.
 *
 * <p>Anders als MT940 liefert CAMT die strukturierten Felder bereits strukturiert - Mandatsreferenz,
 * Gläubigerkennung und Gegenpartei-IBAN stehen in eigenen Elementen und müssen nicht aus einem
 * Verwendungszweck herausgeschnitten werden. Der Parser darf das trotzdem nicht als Einladung
 * verstehen, weniger genau zu sein: welches Element die Gegenpartei trägt, hängt an der Richtung
 * der Buchung, und wer immer den Gläubiger nimmt, verliert bei jeder Gutschrift den Absender.
 *
 * <p>Bewusst namensraumtolerant über die lokalen Elementnamen statt über feste Präfixe: die
 * Ausprägungen {@code camt.052.001.02} und {@code camt.052.001.08} unterscheiden sich im
 * Namensraum, nicht in den hier gelesenen Elementen. Ein fest verdrahteter Namensraum wäre ein
 * Importer, der bei der nächsten Bankumstellung nichts mehr findet und dabei keinen Fehler meldet.
 */
public final class Camt052Parser {

    /** Kennzeichnung des Anfangssaldos. PRCD ist der Vortagessaldo, OPBD der Eröffnungssaldo. */
    private static final List<String> ANFANGSSALDO = List.of("OPBD", "PRCD");

    /** Kennzeichnung des Endsaldos. */
    private static final List<String> ENDSALDO = List.of("CLBD");

    private static final Pattern IBAN_KANDIDAT = Pattern.compile("[A-Z]{2}[0-9]{2}[A-Z0-9]{6,}");

    private Camt052Parser() {}

    public static Parsebefund lies(String inhalt) {
        List<Kontoauszug> auszuege = new ArrayList<>();
        List<Importfehler> fehler = new ArrayList<>();

        Document dokument;
        try {
            dokument = leser().parse(new ByteArrayInputStream(inhalt.getBytes(StandardCharsets.UTF_8)));
        } catch (SAXException | IOException | ParserConfigurationException e) {
            fehler.add(new Importfehler(Invariante.I3, "Datei", "CAMT nicht lesbar: " + e.getMessage()));
            return Parsebefund.von(List.of(), fehler);
        }

        List<Element> reports = nachkommen(dokument.getDocumentElement(), "Rpt");
        if (reports.isEmpty()) {
            fehler.add(new Importfehler(Invariante.I3, "Datei", "Keine Reports gefunden - Element Rpt fehlt."));
            return Parsebefund.von(List.of(), fehler);
        }

        for (Element report : reports) {
            reportLesen(report, auszuege, fehler);
        }
        return Parsebefund.von(auszuege, fehler);
    }

    /**
     * XML-Leser ohne jede Auflösung externer Verweise.
     *
     * <p>Eine Bankdatei ist Fremdeingabe. Ein Parser, der {@code DOCTYPE} akzeptiert, liest auf
     * Zuruf lokale Dateien und öffnet Netzverbindungen - dieselbe Klasse Fehler, wegen der XXE
     * einen Namen hat. Deshalb: Doctype verboten, keine externen DTDs, keine externen Schemata.
     */
    private static DocumentBuilder leser() throws ParserConfigurationException {
        DocumentBuilderFactory fabrik = DocumentBuilderFactory.newInstance();
        fabrik.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        fabrik.setFeature("http://xml.org/sax/features/external-general-entities", false);
        fabrik.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        fabrik.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        fabrik.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        fabrik.setXIncludeAware(false);
        fabrik.setExpandEntityReferences(false);
        fabrik.setNamespaceAware(true);
        return fabrik.newDocumentBuilder();
    }

    // -------------------------------------------------------------------------------------------

    private static void reportLesen(Element report, List<Kontoauszug> auszuege, List<Importfehler> fehler) {
        String nummer = ersterText(report, "ElctrncSeqNb")
                .or(() -> ersterText(report, "LglSeqNb"))
                .or(() -> ersterText(report, "Id"))
                .orElse("?");
        String bezeichnung = "Report " + nummer;

        Iban kontoIban = kind(report, "Acct")
                .flatMap(a -> kind(a, "Id"))
                .flatMap(i -> kind(i, "IBAN"))
                .flatMap(e -> ibanOderBefund(text(e), "Acct/Id/IBAN", bezeichnung, fehler))
                .orElse(null);

        Saldo anfang = saldo(report, ANFANGSSALDO).orElse(null);
        Saldo ende = saldo(report, ENDSALDO).orElse(null);
        if (anfang == null || ende == null) {
            fehler.add(new Importfehler(
                    Invariante.I1, bezeichnung, "Anfangs- oder Endsaldo fehlt (Bal mit Cd OPBD/PRCD bzw. CLBD)."));
            return;
        }

        List<Buchungszeile> zeilen = new ArrayList<>();
        int lauf = 0;
        for (Element eintrag : nachkommen(report, "Ntry")) {
            lauf++;
            eintragLesen(eintrag, bezeichnung, lauf, fehler).ifPresent(zeilen::add);
        }

        auszuege.add(new Kontoauszug(
                Auszugsquelle.CAMT052,
                kontoIban,
                nummer,
                anfang.betrag(),
                ende.betrag(),
                anfang.datum(),
                ende.datum(),
                zeilen));
    }

    private record Saldo(Betrag betrag, LocalDate datum) {}

    private static Optional<Saldo> saldo(Element report, List<String> codes) {
        for (Element bal : nachkommen(report, "Bal")) {
            String code = kind(bal, "Tp")
                    .flatMap(t -> kind(t, "CdOrPrtry"))
                    .flatMap(c -> kind(c, "Cd"))
                    .map(Camt052Parser::text)
                    .orElse("");
            if (!codes.contains(code)) {
                continue;
            }
            Betrag betrag =
                    betragLesen(kind(bal, "Amt").map(Camt052Parser::text).orElse("0"));
            if ("DBIT".equals(kind(bal, "CdtDbtInd").map(Camt052Parser::text).orElse(""))) {
                betrag = betrag.negiert();
            }
            LocalDate datum = kind(bal, "Dt")
                    .flatMap(d -> kind(d, "Dt").or(() -> kind(d, "DtTm")))
                    .map(Camt052Parser::text)
                    .flatMap(Camt052Parser::datumLesen)
                    .orElse(null);
            if (datum == null) {
                continue;
            }
            return Optional.of(new Saldo(betrag, datum));
        }
        return Optional.empty();
    }

    private static Optional<Buchungszeile> eintragLesen(
            Element eintrag, String bezeichnung, int lauf, List<Importfehler> fehler) {

        Betrag betrag =
                betragLesen(kind(eintrag, "Amt").map(Camt052Parser::text).orElse("0"));
        boolean soll = "DBIT"
                .equals(kind(eintrag, "CdtDbtInd").map(Camt052Parser::text).orElse(""));
        if (soll) {
            betrag = betrag.negiert();
        }

        boolean storno = kind(eintrag, "RvslInd")
                .map(Camt052Parser::text)
                .map(t -> "true".equalsIgnoreCase(t) || "1".equals(t))
                .orElse(false);

        LocalDate buchungstag = kind(eintrag, "BookgDt")
                .flatMap(d -> kind(d, "Dt").or(() -> kind(d, "DtTm")))
                .map(Camt052Parser::text)
                .flatMap(Camt052Parser::datumLesen)
                .orElse(null);
        LocalDate valuta = kind(eintrag, "ValDt")
                .flatMap(d -> kind(d, "Dt").or(() -> kind(d, "DtTm")))
                .map(Camt052Parser::text)
                .flatMap(Camt052Parser::datumLesen)
                .orElse(buchungstag);
        if (buchungstag == null) {
            buchungstag = valuta;
        }
        if (buchungstag == null) {
            fehler.add(new Importfehler(
                    Invariante.I3, bezeichnung, "Eintrag " + lauf + " ohne Buchungs- und Valutadatum."));
            return Optional.empty();
        }

        // I3: der Detailblock. In CAMT ist er NtryDtls/TxDtls - ohne ihn fehlen Mandatsreferenz,
        // Gläubigerkennung und Gegenpartei-IBAN, also genau die Felder, wegen derer dieses Schema
        // existiert.
        Optional<Element> details = kind(eintrag, "NtryDtls").flatMap(d -> kind(d, "TxDtls"));
        if (details.isEmpty()) {
            fehler.add(new Importfehler(
                    Invariante.I3, bezeichnung, "Eintrag " + lauf + " ohne Detailblock NtryDtls/TxDtls."));
            return Optional.empty();
        }
        Element tx = details.get();

        String bankreferenz = ersterText(eintrag, "AcctSvcrRef")
                .filter(r -> !r.isBlank())
                .orElseGet(() -> kind(tx, "Refs")
                        .flatMap(r -> kind(r, "TxId"))
                        .map(Camt052Parser::text)
                        .filter(r -> !r.isBlank())
                        .orElse(bezeichnung + ":" + lauf));

        Optional<Element> refs = kind(tx, "Refs");
        String endeZuEnde = refs.flatMap(r -> kind(r, "EndToEndId"))
                .map(Camt052Parser::text)
                .orElse(null);
        String mandat =
                refs.flatMap(r -> kind(r, "MndtId")).map(Camt052Parser::text).orElse(null);

        Optional<Element> parteien = kind(tx, "RltdPties");
        // Bei einer Belastung ist die Gegenpartei der Gläubiger, bei einer Gutschrift der
        // Schuldner. Immer denselben zu nehmen verliert je eine Richtung vollständig.
        //
        // Der Rückfall auf die jeweils andere Partei ist kein Schönheitsfehler, sondern nötig:
        // bei Rücklastschriften und Stornos führen Banken weiterhin den ursprünglichen Gläubiger,
        // obwohl die Buchung eine Gutschrift ist. Ohne den Rückfall verlöre genau diese Klasse von
        // Buchungen ihre Gegenpartei - und damit ihre Klassifizierbarkeit.
        String parteiElement = soll ? "Cdtr" : "Dbtr";
        String andereParteiElement = soll ? "Dbtr" : "Cdtr";
        String kontoElement = soll ? "CdtrAcct" : "DbtrAcct";
        String anderesKontoElement = soll ? "DbtrAcct" : "CdtrAcct";

        String name = parteien.flatMap(p -> kind(p, parteiElement).or(() -> kind(p, andereParteiElement)))
                .flatMap(p -> kind(p, "Nm"))
                .map(Camt052Parser::text)
                .orElse(null);

        Iban gegenpartei = parteien.flatMap(p -> kind(p, kontoElement).or(() -> kind(p, anderesKontoElement)))
                .flatMap(k -> kind(k, "Id"))
                .flatMap(i -> kind(i, "IBAN"))
                .flatMap(e -> ibanOderBefund(text(e), kontoElement + "/Id/IBAN", bezeichnung, fehler))
                .orElse(null);

        // Die Gläubigerkennung steht als "sonstige Kennung" unter der Identität des Gläubigers.
        // Getrennt von der Mandatsreferenz zu speichern ist die Voraussetzung dafür, dass die
        // Acquirer-Regel aus constraint.dauermandat-vs-pos überhaupt berechenbar ist.
        String glaeubiger = parteien.flatMap(p -> kind(p, "Cdtr"))
                .flatMap(c -> kind(c, "Id"))
                .flatMap(Camt052Parser::sonstigeKennung)
                .orElse(null);

        String zweck = kind(tx, "RmtInf")
                .map(r -> nachkommen(r, "Ustrd").stream()
                        .map(Camt052Parser::text)
                        .reduce("", (a, b) -> a.isEmpty() ? b : a + " " + b))
                .filter(t -> !t.isBlank())
                .orElse(null);

        String buchungstext = ersterText(eintrag, "AddtlNtryInf").orElse(null);

        return Optional.of(new Buchungszeile(
                bankreferenz,
                buchungstag,
                valuta,
                betrag,
                storno,
                name,
                gegenpartei,
                mandat,
                glaeubiger,
                endeZuEnde,
                zweck,
                buchungstext));
    }

    /** {@code PrvtId/Othr/Id} oder {@code OrgId/Othr/Id} - dort liegt die SEPA-Gläubigerkennung. */
    private static Optional<String> sonstigeKennung(Element id) {
        for (String traeger : List.of("PrvtId", "OrgId")) {
            Optional<String> kennung = kind(id, traeger)
                    .flatMap(t -> kind(t, "Othr"))
                    .flatMap(o -> kind(o, "Id"))
                    .map(Camt052Parser::text)
                    .filter(t -> !t.isBlank());
            if (kennung.isPresent()) {
                return kennung;
            }
        }
        return Optional.empty();
    }

    // -------------------------------------------------------------------------------------------
    // DOM-Kleinteile. Über lokale Namen, damit der Namensraum der Ausprägung egal ist.
    // -------------------------------------------------------------------------------------------

    private static Optional<Element> kind(Element eltern, String name) {
        NodeList kinder = eltern.getChildNodes();
        for (int i = 0; i < kinder.getLength(); i++) {
            Node knoten = kinder.item(i);
            if (knoten.getNodeType() == Node.ELEMENT_NODE && name.equals(lokalerName(knoten))) {
                return Optional.of((Element) knoten);
            }
        }
        return Optional.empty();
    }

    private static List<Element> nachkommen(Element eltern, String name) {
        List<Element> treffer = new ArrayList<>();
        sammle(eltern, name, treffer);
        return treffer;
    }

    private static void sammle(Element eltern, String name, List<Element> treffer) {
        NodeList kinder = eltern.getChildNodes();
        for (int i = 0; i < kinder.getLength(); i++) {
            Node knoten = kinder.item(i);
            if (knoten.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) knoten;
            if (name.equals(lokalerName(element))) {
                treffer.add(element);
            } else {
                sammle(element, name, treffer);
            }
        }
    }

    private static Optional<String> ersterText(Element eltern, String name) {
        return kind(eltern, name).map(Camt052Parser::text).filter(t -> !t.isBlank());
    }

    private static String lokalerName(Node knoten) {
        return knoten.getLocalName() != null ? knoten.getLocalName() : knoten.getNodeName();
    }

    private static String text(Element element) {
        return element.getTextContent() == null ? "" : element.getTextContent().strip();
    }

    private static Betrag betragLesen(String text) {
        // CAMT schreibt den Punkt als Dezimaltrenner - anders als MT940.
        return new Betrag(new BigDecimal(text.strip()));
    }

    private static Optional<LocalDate> datumLesen(String text) {
        try {
            return Optional.of(LocalDate.parse(text.strip().substring(0, 10)));
        } catch (DateTimeParseException | IndexOutOfBoundsException e) {
            return Optional.empty();
        }
    }

    private static Optional<Iban> ibanOderBefund(
            String text, String herkunft, String bezeichnung, List<Importfehler> fehler) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String normalisiert = Iban.normalisieren(text);
        Optional<Iban> iban = Iban.lesen(normalisiert);
        if (iban.isPresent()) {
            return iban;
        }
        if (IBAN_KANDIDAT.matcher(normalisiert).matches()) {
            fehler.add(new Importfehler(
                    Invariante.I5, bezeichnung, "IBAN-Pruefsumme falsch in " + herkunft + ": " + normalisiert));
        }
        return Optional.empty();
    }
}
