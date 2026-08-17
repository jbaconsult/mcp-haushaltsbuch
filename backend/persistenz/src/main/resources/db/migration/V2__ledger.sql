-- =============================================================================
-- V2 Ledger-Grundschema
--
-- V1 hat bei Benutzer, Konto und Kontozugriff aufgehoert, weil der Ledger-Kern
-- eine offene Entscheidung war. Sie ist mit HB-06 / ADR-0003 getroffen
-- (Eigenbau), damit ist die Fortsetzung frei.
--
-- Zwei Saetze aus ADR-0003 tragen dieses Schema:
--
--   "Intern gilt doppelte Buchfuehrung als Mechanismus: jede Bewegung hat zwei
--    Seiten, das Kartenkonto ist ein Verbindlichkeitskonto, die Sammelabbuchung
--    ist ein Transfer zwischen Aktiv- und Passivkonto."
--   "An der Oberflaeche erscheint davon nichts."
--
-- Und aus ADR-0004: die Split-Tabelle IST die Positionsebene, Kategorien sind
-- eine Dimension am Split und kein Konto.
--
-- Nicht enthalten und bewusst nicht vorbereitet: Toepfe und ihre Nullsummen-
-- Invariante (HB-06 Stufe 2), die Kennzahl "verfuegbar", Klassifikationsregeln,
-- Sichtbarkeitsstufen aus ADR-0006 (Status Vorgeschlagen, nicht ratifiziert).
-- =============================================================================


-- -----------------------------------------------------------------------------
-- Sichtbarkeitshelfer
--
-- Dieselbe Bedingung stand in V1 dreimal ausgeschrieben in den Policies. Bei
-- sechs weiteren Tabellen waeren es fuenfzehn Kopien - und die fuenfzehnte
-- weicht ab. Die Funktionen sind STABLE und laufen als Aufrufer, damit die
-- Policies auf "kontozugriff" weiterhin greifen.
--
-- Beide liefern FALSE, wenn kein Benutzerkontext gesetzt ist: aktueller_benutzer()
-- ist dann NULL, der Vergleich ergibt NULL, EXISTS ergibt FALSE. Fail-Closed.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION konto_lesbar(p_konto uuid) RETURNS boolean
    LANGUAGE sql
    STABLE
AS $$
    SELECT EXISTS (SELECT 1
                     FROM kontozugriff kz
                    WHERE kz.konto_id = p_konto
                      AND kz.benutzer_id = aktueller_benutzer())
$$;

COMMENT ON FUNCTION konto_lesbar(uuid) IS
    'Ob der Benutzer der laufenden Transaktion dieses Konto sehen darf. NULL-Kontext ergibt FALSE.';

CREATE OR REPLACE FUNCTION konto_schreibbar(p_konto uuid) RETURNS boolean
    LANGUAGE sql
    STABLE
AS $$
    SELECT EXISTS (SELECT 1
                     FROM kontozugriff kz
                    WHERE kz.konto_id = p_konto
                      AND kz.benutzer_id = aktueller_benutzer()
                      AND kz.recht = 'SCHREIBEN')
$$;

COMMENT ON FUNCTION konto_schreibbar(uuid) IS
    'Ob der Benutzer der laufenden Transaktion dieses Konto aendern darf. NULL-Kontext ergibt FALSE.';


-- -----------------------------------------------------------------------------
-- Bewegung
--
-- Eine Geldbewegung. Hat eine Seite, wenn Geld den Haushalt verlaesst oder
-- hereinkommt, und zwei, wenn es zwischen eigenen Konten wandert.
--
-- Der Grund fuer diese Tabelle ist die Doppelzaehlung von Kartenumsaetzen. Die
-- Sammelabbuchung auf dem Girokonto und die Einzelumsaetze auf dem Kartenkonto
-- beschreiben denselben Geldfluss. Eine Auswertungsregel der Form "Sammelposten
-- ueberspringen" muss bei jeder neuen Auswertung erneut mitgedacht werden - und
-- irgendeine denkt nicht daran.
--
-- Hier ist es keine Regel, sondern eine Invariante: eine Bewegung mit zwei
-- Seiten ergaenzt sich zu null und traegt keine Kategorie (siehe Trigger unten).
-- Eine Kategorienauswertung summiert Splits mit Kategorie - der Ausgleich der
-- Karte hat strukturell keine, die Einzelumsaetze haben je genau eine. Doppelt
-- zaehlen ist nicht "verboten", sondern nicht formulierbar.
--
-- Die Tabelle hat ausser der Kennung nichts. Das ist Absicht: alles Fachliche
-- haengt an den Seiten. Ob eine einseitige Bewegung spaeter zur zweiseitigen
-- wird, ist Sache der Klassifikation und nicht dieses Sub-Sprints.
-- -----------------------------------------------------------------------------
CREATE TABLE bewegung (
    id           uuid         PRIMARY KEY,
    angelegt_am  timestamptz  NOT NULL DEFAULT now()
);

COMMENT ON TABLE bewegung IS
    'Eine Geldbewegung mit einer oder zwei Seiten. Zweiseitig heisst Umbuchung zwischen eigenen Konten.';


-- -----------------------------------------------------------------------------
-- Kategorien
--
-- Benutzereditierbare Taxonomie mit genau einer Ebene Gruppierung darueber, als
-- Dimension am Split. Kein Kontenrahmen - siehe ADR-0004.
--
-- Drei Wartungspflichten aus ADR-0004, alle drei stehen hier im Schema:
--   1. Stabile Kennung. Deshalb uuid und nicht die Bezeichnung als Schluessel:
--      Umbenennen darf keine Regel und keine Historie brechen.
--   2. Loeschen mit Merge oder Sperre statt Kaskade. Deshalb RESTRICT auf der
--      Fremdschluesselkante vom Split - eine Kategorie kann nicht verschwinden
--      und Splits verwaisen lassen. Wer sie loswerden will, buchte vorher um.
--   3. Die Gruppierungsebene von Anfang an, nicht als spaetere Migration.
--
-- "aktiv" ist der Weg, eine Kategorie aus der Auswahl zu nehmen, ohne die
-- Historie anzutasten. Das ist der haeufigere Fall als echtes Loeschen.
-- -----------------------------------------------------------------------------
CREATE TABLE kategoriegruppe (
    id           uuid         PRIMARY KEY,
    bezeichnung  text         NOT NULL CHECK (bezeichnung <> ''),
    sortierung   integer      NOT NULL DEFAULT 0,
    angelegt_am  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT kategoriegruppe_bezeichnung_eindeutig UNIQUE (bezeichnung)
);

COMMENT ON TABLE kategoriegruppe IS
    'Eine Ebene Gruppierung ueber den Kategorien. Von Anfang an da, weil sie spaeter eine Migration waere.';

CREATE TABLE kategorie (
    id           uuid         PRIMARY KEY,
    gruppe_id    uuid         NOT NULL REFERENCES kategoriegruppe (id) ON DELETE RESTRICT,
    bezeichnung  text         NOT NULL CHECK (bezeichnung <> ''),
    aktiv        boolean      NOT NULL DEFAULT true,
    angelegt_am  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT kategorie_bezeichnung_eindeutig UNIQUE (bezeichnung)
);

COMMENT ON TABLE kategorie IS
    'Benutzereditierbare Kategorie. Dimension am Split, kein Konto.';

CREATE INDEX kategorie_gruppe_idx ON kategorie (gruppe_id);


-- -----------------------------------------------------------------------------
-- Kontoauszug
--
-- Die Klammer, an der die Saldenvalidierung haengt. Ohne sie ist I1 - Anfangs-
-- saldo plus Summe der Buchungen gleich Endsaldo - nachtraeglich nicht mehr
-- pruefbar, weil niemand mehr weiss, welche Buchungen zu welchem Auszug
-- gehoerten.
--
-- Anfangs- und Endsaldo stehen hier so, wie die Bank sie geliefert hat. Sie
-- werden nicht aus den Buchungen berechnet - genau der Vergleich zwischen
-- Geliefertem und Gerechnetem ist die Pruefung.
--
-- Der eindeutige Schluessel ueber Konto, Auszugsnummer und Zeitraum traegt die
-- Idempotenz: derselbe Auszug ein zweites Mal eingelesen findet seine Zeile
-- wieder, statt eine zweite anzulegen. Die Auszugsnummer allein reicht nicht,
-- weil viele Banken sie jaehrlich zuruecksetzen.
-- -----------------------------------------------------------------------------
CREATE TABLE kontoauszug (
    id             uuid           PRIMARY KEY,
    konto_id       uuid           NOT NULL REFERENCES konto (id) ON DELETE RESTRICT,
    auszugsnummer  text           NOT NULL CHECK (auszugsnummer <> ''),
    quelle         text           NOT NULL CHECK (quelle IN ('MT940', 'CAMT052')),
    anfangssaldo   numeric(14,2)  NOT NULL,
    endsaldo       numeric(14,2)  NOT NULL,
    von            date           NOT NULL,
    bis            date           NOT NULL,
    importiert_am  timestamptz    NOT NULL DEFAULT now(),
    CONSTRAINT kontoauszug_zeitraum          CHECK (von <= bis),
    CONSTRAINT kontoauszug_je_konto_eindeutig UNIQUE (konto_id, auszugsnummer, von, bis)
);

COMMENT ON TABLE kontoauszug IS
    'Ein eingelesener Auszug bzw. CAMT-Report mit den von der Bank gelieferten Salden.';


-- -----------------------------------------------------------------------------
-- Buchung
--
-- Eine Seite einer Bewegung, verankert an genau einem Konto. In der Praxis: eine
-- Zeile des Kontoauszugs.
--
-- Vorzeichen: negativ ist Abgang, positiv ist Zugang, immer aus Sicht des
-- Kontos. Ein Verbindlichkeitskonto - das Kartenkonto - traegt damit einen
-- negativen Saldo, solange Schuld offen ist. Das ist kein Sonderfall, sondern
-- dieselbe Rechnung.
--
-- ZUR ZENTRALEN ANFORDERUNG DIESES SCHEMAS: die strukturierten Felder stehen
-- EINZELN. Gegenpartei-Name, Gegenpartei-IBAN, Mandatsreferenz,
-- Glaeubigerkennung und End-zu-Ende-Referenz sind eigene Spalten und werden
-- nicht zu einem Textblob zusammengeklebt, auch nicht vorlaeufig.
--
-- Der Grund ist gemessen, nicht theoretisch. Eine Namensheuristik hat in der
-- Analyse aus Phase 1 zweimal vierstellige Posten verschluckt: eine monatliche
-- Darlehensrate, weil die Gegenpartei auf beide Kontoinhaber lautete und als
-- interne Umbuchung gefiltert wurde, und eine Steuererstattung, weil die Bank
-- abgekuerzt schreibt und der Filter den ausgeschriebenen Begriff suchte.
-- Deshalb gilt constraint.klassifikation-iban-mref: klassifiziert wird ueber
-- IBAN, Mandatsreferenz und Glaeubigerkennung, nie ueber Textmuster im Namen.
--
-- Und deshalb ist auch die Trennung von Mandatsreferenz und Glaeubigerkennung
-- keine Kosmetik: die Acquirer-Heuristik aus constraint.dauermandat-vs-pos -
-- eine Glaeubigerkennung mit mehr als drei verschiedenen Mandatsreferenzen ist
-- ein Zahlungsdienstleister und ihre Buchungen sind keine Mandate - ist nur
-- berechenbar, wenn beide Felder getrennt vorliegen.
--
-- Die Klassifikation selbst ist NICHT Teil dieses Sub-Sprints. Dieses Schema
-- sorgt nur dafuer, dass ihre Eingangsdaten nicht schon beim Import verloren
-- gehen.
-- -----------------------------------------------------------------------------
CREATE TABLE buchung (
    id                   uuid           PRIMARY KEY,

    -- RESTRICT und nicht CASCADE: eine Bewegung darf ihre Seiten nicht mit sich
    -- reissen. Wer eine Bewegung aufloest, entfernt zuerst ihre Buchungen und
    -- braucht dafuer SCHREIBEN auf deren Konten.
    bewegung_id          uuid           NOT NULL REFERENCES bewegung (id)    ON DELETE RESTRICT,
    konto_id             uuid           NOT NULL REFERENCES konto (id)       ON DELETE RESTRICT,

    -- NULL erlaubt: eine von Hand erfasste Buchung - Bargeld - hat keinen
    -- Auszug. Das ist kein Fehler, sondern der Normalfall abseits der Bank.
    kontoauszug_id       uuid           REFERENCES kontoauszug (id)          ON DELETE RESTRICT,

    buchungstag          date           NOT NULL,
    valuta               date           NOT NULL,
    betrag               numeric(14,2)  NOT NULL,

    -- Storno im Sinne von MT940 RC/RD bzw. CAMT RvslInd. Bewusst ein eigenes
    -- Feld: eine Stornobuchung ist eine echte Buchung mit umgekehrtem Vorzeichen
    -- und darf nicht aus dem Bestand verschwinden, sonst geht I1 nicht mehr auf.
    storno               boolean        NOT NULL DEFAULT false,

    -- Grundlage der Deduplizierung (I4). Exportzeitraeume ueberlappen sich an
    -- den Randtagen; ohne diesen Schluessel entstehen Doubletten.
    bankreferenz         text           NOT NULL CHECK (bankreferenz <> ''),

    -- Die strukturierten Felder. Einzeln, siehe Begruendung oben.
    gegenpartei_name     text,
    gegenpartei_iban     text,
    mandatsreferenz      text,
    glaeubigerkennung    text,
    endezuende_referenz  text,
    verwendungszweck     text,
    buchungstext         text,

    angelegt_am          timestamptz    NOT NULL DEFAULT now(),

    CONSTRAINT buchung_bankreferenz_je_konto UNIQUE (konto_id, bankreferenz)
);

COMMENT ON TABLE buchung IS
    'Eine Seite einer Bewegung an einem Konto. Strukturierte Felder einzeln, nie als Textblob.';

COMMENT ON COLUMN buchung.betrag IS
    'Negativ ist Abgang, positiv ist Zugang, aus Sicht des Kontos.';
COMMENT ON COLUMN buchung.bankreferenz IS
    'Schluessel der Deduplizierung (I4). Bei Belegen ohne Bankreferenz vom Importer inhaltsstabil abgeleitet.';
COMMENT ON COLUMN buchung.gegenpartei_iban IS
    'IBAN der Gegenpartei. Erstes Kriterium der Klassifikation - vor Mandatsreferenz, lange vor Namenstext.';
COMMENT ON COLUMN buchung.mandatsreferenz IS
    'MREF. Getrennt von der Glaeubigerkennung, sonst ist die Acquirer-Heuristik nicht berechenbar.';
COMMENT ON COLUMN buchung.glaeubigerkennung IS
    'CRED. Mehr als drei verschiedene Mandatsreferenzen unter einer Kennung bedeuten Zahlungsdienstleister.';

CREATE INDEX buchung_konto_valuta_idx      ON buchung (konto_id, valuta);
CREATE INDEX buchung_bewegung_idx          ON buchung (bewegung_id);
CREATE INDEX buchung_kontoauszug_idx       ON buchung (kontoauszug_id);
-- Indizes auf den Klassifikationsfeldern. Sie sind der Zugriffspfad jeder
-- kuenftigen Regel und der Acquirer-Heuristik; partiell, weil die Mehrzahl der
-- Buchungen weder Mandat noch Gegenpartei-IBAN traegt.
CREATE INDEX buchung_gegenpartei_iban_idx  ON buchung (gegenpartei_iban)  WHERE gegenpartei_iban IS NOT NULL;
CREATE INDEX buchung_glaeubiger_mandat_idx ON buchung (glaeubigerkennung, mandatsreferenz)
                                            WHERE glaeubigerkennung IS NOT NULL;


-- -----------------------------------------------------------------------------
-- Buchungssplit
--
-- Die Positionsebene. Keine zusaetzliche Struktur neben der Buchung, sondern
-- diese Tabelle - ADR-0004 hat eine getrennte Positionstabelle ausdruecklich
-- verworfen, weil sie dieselbe Information ein zweites Mal fuehrt und eine
-- zweite Summeninvariante braucht, die mit der ersten auseinanderlaeuft.
--
-- Anfangs hat jede Buchung genau einen Split. "Aufschluesseln" ersetzt spaeter
-- den einen durch mehrere. Keine Migration, kein Sonderfall - nur eine andere
-- Anzahl Zeilen.
--
-- kategorie_id ist NULL-bar, und das ist der Normalzustand direkt nach dem
-- Import: der Importer kategorisiert nicht. Ein Importer, der unterwegs schon
-- kategorisiert, macht den spaeteren Trockenlauf-Modus unmoeglich, den die
-- Reichweitenpruefung aus constraint.regelvorschlag-reichweite braucht. NULL
-- heisst "gehoert in die Review-Queue", nicht "Fehler".
-- -----------------------------------------------------------------------------
CREATE TABLE buchungssplit (
    id            uuid           PRIMARY KEY,
    -- CASCADE ist hier richtig, anders als bei der Kategorie: ein Split ohne
    -- seine Buchung ist bedeutungslos, eine Kategorie ohne Split nicht.
    buchung_id    uuid           NOT NULL REFERENCES buchung (id)   ON DELETE CASCADE,
    kategorie_id  uuid           REFERENCES kategorie (id)          ON DELETE RESTRICT,
    betrag        numeric(14,2)  NOT NULL,
    notiz         text,
    angelegt_am   timestamptz    NOT NULL DEFAULT now()
);

COMMENT ON TABLE buchungssplit IS
    'Gegenposten einer Buchung mit Kategorie. Die Positionsebene selbst - siehe ADR-0004.';
COMMENT ON COLUMN buchungssplit.kategorie_id IS
    'NULL heisst nicht kategorisiert und damit Review-Queue. Der Importer setzt hier nie einen Wert.';

CREATE INDEX buchungssplit_buchung_idx   ON buchungssplit (buchung_id);
CREATE INDEX buchungssplit_kategorie_idx ON buchungssplit (kategorie_id) WHERE kategorie_id IS NOT NULL;


-- -----------------------------------------------------------------------------
-- Invariante: Summe der Splits gleich Buchungsbetrag
--
-- Sie steht in der Datenbank und nicht im Java-Code, weil sie sonst genau dann
-- fehlt, wenn jemand einmal an der Anwendung vorbei schreibt - beim Import,
-- beim Reparaturskript, beim Migrationsschritt.
--
-- DEFERRABLE INITIALLY DEFERRED ist notwendig, nicht bequem: eine Buchung wird
-- vor ihren Splits eingefuegt und ist zwischen den beiden Anweisungen zwingend
-- unausgeglichen. Geprueft wird am Ende der Transaktion, wenn die Frage
-- ueberhaupt beantwortbar ist.
--
-- Zur Zugriffskontrolle: die Funktion laeuft als Aufrufer und sieht damit nur,
-- was der Aufrufer sehen darf. Das ist die richtige Wahl - wer eine Buchung
-- nicht sehen darf, darf ihre Splits ohnehin nicht schreiben, die Policies
-- lehnen das vorher ab. Eine Funktion, die hier mehr sieht als der Aufrufer,
-- waere ein Weg, ueber Fehlermeldungen fremde Betraege zu erraten.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION pruefe_splitsumme(p_buchung uuid) RETURNS void
    LANGUAGE plpgsql
AS $$
DECLARE
    v_betrag  numeric(14,2);
    v_summe   numeric(14,2);
    v_anzahl  integer;
BEGIN
    SELECT b.betrag INTO v_betrag FROM buchung b WHERE b.id = p_buchung;

    -- Buchung in derselben Transaktion geloescht; ihre Splits sind per CASCADE
    -- mitgegangen. Es gibt nichts mehr auszugleichen.
    IF NOT FOUND THEN
        RETURN;
    END IF;

    SELECT count(*), coalesce(sum(s.betrag), 0)
      INTO v_anzahl, v_summe
      FROM buchungssplit s
     WHERE s.buchung_id = p_buchung;

    IF v_anzahl = 0 THEN
        RAISE EXCEPTION 'Buchung % hat keinen Split. Jede Buchung hat mindestens einen.', p_buchung
            USING ERRCODE = 'check_violation';
    END IF;

    IF v_summe <> v_betrag THEN
        RAISE EXCEPTION 'Splitsumme % weicht vom Buchungsbetrag % ab (Buchung %).',
            v_summe, v_betrag, p_buchung
            USING ERRCODE = 'check_violation';
    END IF;
END
$$;

CREATE OR REPLACE FUNCTION buchung_splitsumme_wache() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM pruefe_splitsumme(NEW.id);
    RETURN NULL;
END
$$;

CREATE OR REPLACE FUNCTION buchungssplit_splitsumme_wache() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM pruefe_splitsumme(OLD.buchung_id);
    ELSE
        PERFORM pruefe_splitsumme(NEW.buchung_id);
        -- Umhaengen an eine andere Buchung laesst die alte unausgeglichen zurueck.
        IF TG_OP = 'UPDATE' AND OLD.buchung_id <> NEW.buchung_id THEN
            PERFORM pruefe_splitsumme(OLD.buchung_id);
        END IF;
    END IF;
    RETURN NULL;
END
$$;

CREATE CONSTRAINT TRIGGER buchung_splitsumme
    AFTER INSERT OR UPDATE ON buchung
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION buchung_splitsumme_wache();

CREATE CONSTRAINT TRIGGER buchungssplit_splitsumme
    AFTER INSERT OR UPDATE OR DELETE ON buchungssplit
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION buchungssplit_splitsumme_wache();


-- -----------------------------------------------------------------------------
-- Invariante: zweiseitige Bewegungen
--
-- Das ist die Stelle, an der die Doppelzaehlung strukturell unmoeglich wird.
--
-- Eine Bewegung mit einer Seite ist Geld, das den Haushalt verlaesst oder
-- hereinkommt. Ungeprueft, weil es nichts zu pruefen gibt.
--
-- Eine Bewegung mit zwei oder mehr Seiten ist eine Umbuchung zwischen eigenen
-- Konten - Sammelabbuchung der Karte, Privatentnahme, Speisung des Haushalts-
-- kontos. Fuer sie gelten zwei Bedingungen:
--
--   1. Die Seiten ergaenzen sich zu null. Was von einem Konto abgeht, kommt auf
--      einem anderen an. Geht es nicht auf, ist eine Seite falsch erfasst.
--   2. Keine Seite traegt eine Kategorie. Eine Umbuchung ist kein Aufwand.
--
-- Bedingung 2 ist die eigentliche Sicherung. Eine Auswertung nach Kategorien
-- summiert Splits mit Kategorie. Der Ausgleich der Kreditkarte kann keine
-- haben - die Datenbank laesst es nicht zu -, die Einzelumsaetze auf dem
-- Kartenkonto haben je genau eine. Beide zu zaehlen ist damit nicht verboten,
-- sondern nicht formulierbar, und keine Auswertungsregel muss daran denken.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION pruefe_bewegung(p_bewegung uuid) RETURNS void
    LANGUAGE plpgsql
AS $$
DECLARE
    v_seiten        integer;
    v_summe         numeric(14,2);
    v_kategorisiert integer;
BEGIN
    SELECT count(*), coalesce(sum(b.betrag), 0)
      INTO v_seiten, v_summe
      FROM buchung b
     WHERE b.bewegung_id = p_bewegung;

    IF v_seiten < 2 THEN
        RETURN;
    END IF;

    IF v_summe <> 0 THEN
        RAISE EXCEPTION
            'Bewegung % hat % Seiten, die sich nicht zu null ergaenzen (Summe %). Eine Umbuchung zwischen eigenen Konten verliert kein Geld.',
            p_bewegung, v_seiten, v_summe
            USING ERRCODE = 'check_violation';
    END IF;

    SELECT count(*)
      INTO v_kategorisiert
      FROM buchungssplit s
      JOIN buchung b ON b.id = s.buchung_id
     WHERE b.bewegung_id = p_bewegung
       AND s.kategorie_id IS NOT NULL;

    IF v_kategorisiert > 0 THEN
        RAISE EXCEPTION
            'Bewegung % ist eine Umbuchung zwischen eigenen Konten und darf keine Kategorie tragen (% kategorisierte Splits). Sonst zaehlt die Auswertung sie zusaetzlich zu den Einzelumsaetzen.',
            p_bewegung, v_kategorisiert
            USING ERRCODE = 'check_violation';
    END IF;
END
$$;

CREATE OR REPLACE FUNCTION buchung_bewegung_wache() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP <> 'INSERT' THEN
        PERFORM pruefe_bewegung(OLD.bewegung_id);
    END IF;
    IF TG_OP <> 'DELETE' THEN
        PERFORM pruefe_bewegung(NEW.bewegung_id);
    END IF;
    RETURN NULL;
END
$$;

CREATE OR REPLACE FUNCTION buchungssplit_bewegung_wache() RETURNS trigger
    LANGUAGE plpgsql
AS $$
DECLARE
    v_bewegung uuid;
BEGIN
    SELECT b.bewegung_id INTO v_bewegung FROM buchung b WHERE b.id = NEW.buchung_id;
    IF FOUND THEN
        PERFORM pruefe_bewegung(v_bewegung);
    END IF;
    RETURN NULL;
END
$$;

CREATE CONSTRAINT TRIGGER buchung_bewegung
    AFTER INSERT OR UPDATE OR DELETE ON buchung
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION buchung_bewegung_wache();

CREATE CONSTRAINT TRIGGER buchungssplit_bewegung
    AFTER INSERT OR UPDATE ON buchungssplit
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION buchungssplit_bewegung_wache();


-- =============================================================================
-- Zugriffskontrolle
--
-- ENABLE und FORCE auf jeder Tabelle, aus demselben Grund wie in V1: ENABLE
-- allein nimmt den Tabelleneigentuemer von saemtlichen Policies aus, und eine
-- Zugriffskontrolle, die der Eigentuemer stillschweigend umgeht, ist der
-- Anschein einer Zugriffskontrolle.
-- =============================================================================
ALTER TABLE bewegung        ENABLE ROW LEVEL SECURITY;
ALTER TABLE bewegung        FORCE  ROW LEVEL SECURITY;
ALTER TABLE kategoriegruppe ENABLE ROW LEVEL SECURITY;
ALTER TABLE kategoriegruppe FORCE  ROW LEVEL SECURITY;
ALTER TABLE kategorie       ENABLE ROW LEVEL SECURITY;
ALTER TABLE kategorie       FORCE  ROW LEVEL SECURITY;
ALTER TABLE kontoauszug     ENABLE ROW LEVEL SECURITY;
ALTER TABLE kontoauszug     FORCE  ROW LEVEL SECURITY;
ALTER TABLE buchung         ENABLE ROW LEVEL SECURITY;
ALTER TABLE buchung         FORCE  ROW LEVEL SECURITY;
ALTER TABLE buchungssplit   ENABLE ROW LEVEL SECURITY;
ALTER TABLE buchungssplit   FORCE  ROW LEVEL SECURITY;


-- -----------------------------------------------------------------------------
-- Kategorien
--
-- Haushaltsweit sichtbar fuer jeden angemeldeten Benutzer, und das ist eine
-- Entscheidung und kein Versehen: HB-05 stellt fest, dass es innerhalb der Ehe
-- keinen Geheimhaltungsbedarf gibt. Die Taxonomie ist gemeinsames Vokabular -
-- zwei Menschen mit verschiedenen Kategorienlisten koennen ueber denselben
-- Haushalt nicht reden.
--
-- Ohne Benutzerkontext bleibt trotzdem alles unsichtbar. Fail-Closed gilt auch
-- fuer harmlose Daten, sonst ist die Regel nicht mehr einheitlich pruefbar.
-- -----------------------------------------------------------------------------
CREATE POLICY kategoriegruppe_haushaltsweit ON kategoriegruppe
    FOR ALL
    USING      (aktueller_benutzer() IS NOT NULL)
    WITH CHECK (aktueller_benutzer() IS NOT NULL);

CREATE POLICY kategorie_haushaltsweit ON kategorie
    FOR ALL
    USING      (aktueller_benutzer() IS NOT NULL)
    WITH CHECK (aktueller_benutzer() IS NOT NULL);


-- -----------------------------------------------------------------------------
-- Kontoauszug und Buchung
--
-- Sichtbar mit LESEN, aenderbar mit SCHREIBEN - getrennte Policies, weil Lesen
-- und Schreiben verschiedene Rechte sind. Ein gemeinsames FOR ALL wuerde beides
-- gleichsetzen.
-- -----------------------------------------------------------------------------
CREATE POLICY kontoauszug_sichtbar ON kontoauszug
    FOR SELECT USING (konto_lesbar(konto_id));
CREATE POLICY kontoauszug_anlegbar ON kontoauszug
    FOR INSERT WITH CHECK (konto_schreibbar(konto_id));
CREATE POLICY kontoauszug_aenderbar ON kontoauszug
    FOR UPDATE USING (konto_schreibbar(konto_id)) WITH CHECK (konto_schreibbar(konto_id));
CREATE POLICY kontoauszug_entfernbar ON kontoauszug
    FOR DELETE USING (konto_schreibbar(konto_id));

CREATE POLICY buchung_sichtbar ON buchung
    FOR SELECT USING (konto_lesbar(konto_id));
CREATE POLICY buchung_anlegbar ON buchung
    FOR INSERT WITH CHECK (konto_schreibbar(konto_id));
CREATE POLICY buchung_aenderbar ON buchung
    FOR UPDATE USING (konto_schreibbar(konto_id)) WITH CHECK (konto_schreibbar(konto_id));
CREATE POLICY buchung_entfernbar ON buchung
    FOR DELETE USING (konto_schreibbar(konto_id));


-- -----------------------------------------------------------------------------
-- Buchungssplit
--
-- Erbt die Sichtbarkeit von seiner Buchung. Bewusst kein eigener Kontobezug als
-- Spalte: eine zweite Kopie derselben Zuordnung wuerde irgendwann von der ersten
-- abweichen, und dann entscheidet die falsche darueber, wer was sieht.
-- -----------------------------------------------------------------------------
CREATE POLICY buchungssplit_sichtbar ON buchungssplit
    FOR SELECT
    USING (EXISTS (SELECT 1 FROM buchung b
                    WHERE b.id = buchungssplit.buchung_id
                      AND konto_lesbar(b.konto_id)));

CREATE POLICY buchungssplit_anlegbar ON buchungssplit
    FOR INSERT
    WITH CHECK (EXISTS (SELECT 1 FROM buchung b
                         WHERE b.id = buchungssplit.buchung_id
                           AND konto_schreibbar(b.konto_id)));

CREATE POLICY buchungssplit_aenderbar ON buchungssplit
    FOR UPDATE
    USING (EXISTS (SELECT 1 FROM buchung b
                    WHERE b.id = buchungssplit.buchung_id
                      AND konto_schreibbar(b.konto_id)))
    WITH CHECK (EXISTS (SELECT 1 FROM buchung b
                         WHERE b.id = buchungssplit.buchung_id
                           AND konto_schreibbar(b.konto_id)));

CREATE POLICY buchungssplit_entfernbar ON buchungssplit
    FOR DELETE
    USING (EXISTS (SELECT 1 FROM buchung b
                    WHERE b.id = buchungssplit.buchung_id
                      AND konto_schreibbar(b.konto_id)));


-- -----------------------------------------------------------------------------
-- Bewegung
--
-- Sichtbar fuer jeden angemeldeten Benutzer, und das ist eine Entscheidung mit
-- Begruendung - keine vergessene Bedingung.
--
-- Diese Zeile besteht aus einer Kennung und einem Zeitstempel. Alles Fachliche
-- haengt an den Seiten, und die stehen hinter der Policy von "buchung". Wer eine
-- Bewegung sieht, deren Seiten ihm verborgen sind, erfaehrt: es gab eine
-- Bewegung. Keinen Betrag, kein Konto, keine Gegenpartei.
--
-- Der naheliegende Entwurf - sichtbar, sobald eine Seite sichtbar ist - kostet
-- mehr, als er bringt. Er macht jedes "INSERT ... RETURNING id" unmoeglich:
-- Postgres prueft die Lesebedingung fuer die zurueckgelieferte Zeile, und in
-- genau dem Moment existiert die erste Seite noch nicht. Der Fehler lautet dann
-- "new row violates row-level security policy" und zeigt auf das Anlegen,
-- obwohl das Lesen gemeint ist. Diesen Preis fuer den Schutz einer Kennung und
-- eines Zeitstempels zu zahlen, waere die falsche Rechnung.
--
-- Unabhaengig davon bleibt die dokumentierte Grenze aus
-- constraint.autorisierung-der-antwort bestehen: eine Privatentnahme ist EINE
-- Bewegung mit zwei Seiten, und wer die eine Seite sehen darf, erfaehrt Betrag
-- und Datum der Kante. Das steht dort als "Transfer-Leck" und ist prinzipiell.
--
-- Kein UPDATE: an einer Bewegung gibt es nichts zu aendern. Entfernen ist
-- erlaubt, laeuft aber gegen den RESTRICT-Fremdschluessel der Buchung - nur eine
-- Bewegung ohne Seiten geht wirklich weg, und deren Seiten zu entfernen setzt
-- SCHREIBEN auf ihren Konten voraus.
-- -----------------------------------------------------------------------------
CREATE POLICY bewegung_sichtbar ON bewegung
    FOR SELECT USING (aktueller_benutzer() IS NOT NULL);

CREATE POLICY bewegung_anlegbar ON bewegung
    FOR INSERT WITH CHECK (aktueller_benutzer() IS NOT NULL);

CREATE POLICY bewegung_entfernbar ON bewegung
    FOR DELETE USING (aktueller_benutzer() IS NOT NULL);


-- -----------------------------------------------------------------------------
-- Rechte der Anwendungsrolle
--
-- Die Tabellenrechte deckt ALTER DEFAULT PRIVILEGES aus V1 bereits ab. Die
-- Funktionen nicht: EXECUTE liegt zwar per Voreinstellung bei PUBLIC, aber
-- diese Voreinstellung ist eine Servereinstellung und keine Zusage dieses
-- Schemas. Wer sie haerter setzt, soll nicht daran scheitern, dass eine Policy
-- ihre Hilfsfunktion nicht mehr aufrufen darf.
-- -----------------------------------------------------------------------------
GRANT EXECUTE ON FUNCTION konto_lesbar(uuid)     TO haushaltsbuch_app;
GRANT EXECUTE ON FUNCTION konto_schreibbar(uuid) TO haushaltsbuch_app;
GRANT EXECUTE ON FUNCTION pruefe_splitsumme(uuid) TO haushaltsbuch_app;
GRANT EXECUTE ON FUNCTION pruefe_bewegung(uuid)   TO haushaltsbuch_app;
