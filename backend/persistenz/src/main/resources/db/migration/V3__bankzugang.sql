-- =============================================================================
-- V3 Bankzugang: externe Konten und ihre Salden
--
-- Legt Kontodaten eines Bankanbieters NEBEN das Ledger, nicht hinein. Buchungen
-- entstehen ausschliesslich ueber den Importdienst, weil sie dort gegen die
-- Saldeninvarianten I1 bis I5 geprueft werden. Ein Nebenweg, der daran vorbei
-- schreibt, hebelt die Selbstvalidierung aus - deshalb gibt es hier keine
-- Buchungstabelle.
--
-- V1 und V2 werden nicht angefasst.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- Bankzugang
--
-- Ein Zugang ist kein Aufruf, sondern ein Vorgang mit Zustand und Ablaufdatum.
-- Die Autorisierung durch einen Menschen gilt begrenzt - bei den hier
-- relevanten Instituten in der Groessenordnung von 180 Tagen. Ohne
-- Ablaufzeitpunkt merkt niemand, dass ein Zugang stirbt; ohne Status ist ein
-- abgebrochener Vorgang von einem funktionierenden nicht zu unterscheiden.
--
-- Der Zustandswert (state) steht hier und nicht in einer eigenen Tabelle: er
-- gehoert zu genau einem Vorgang, und eine neue Autorisierung desselben Zugangs
-- ersetzt ihn. Drei Spalten, weil alle drei geprueft werden muessen - Wert,
-- Gueltigkeit und ob er schon verbraucht ist.
-- -----------------------------------------------------------------------------
CREATE TABLE bankzugang (
    id                      uuid         PRIMARY KEY,

    -- Welcher Anbieter diesen Zugang vermittelt. Als Text und nicht als
    -- Aufzaehlung: der Anbieter ist Konfiguration, und eine Fremdinstallation
    -- bringt einen anderen mit. Eine CHECK-Liste muesste dafuer migriert werden.
    anbieter                text         NOT NULL,

    -- Institutskennung des Anbieters, zerlegt in Name und Land. Zusammen
    -- eindeutig; dasselbe Institut kann in mehreren Laendern auftreten.
    institut_name           text         NOT NULL,
    institut_land           text         NOT NULL,

    -- Anzeigename zum Zeitpunkt der Einrichtung. Bewusst kopiert und nicht
    -- jedes Mal neu geholt: die Liste des Anbieters ist ein Netzaufruf, und ein
    -- Zugang muss auch dann anzeigbar sein, wenn der Anbieter gerade nicht
    -- antwortet.
    institutsname           text         NOT NULL,

    status                  text         NOT NULL
                                         CHECK (status IN ('NICHT_AUTORISIERT',
                                                           'AUTORISIERUNG_LAEUFT',
                                                           'AUTORISIERT',
                                                           'ABGELAUFEN',
                                                           'FEHLGESCHLAGEN')),

    -- Wann die Autorisierung verfaellt. NULL, solange nicht autorisiert.
    gueltig_bis             timestamptz,

    -- Sitzungskennung beim Anbieter. Opak; das System stellt nichts darueber
    -- fest, ausser dass sie die Sitzung wiederfindet.
    sitzung                 text,

    -- Meldung des Anbieters bei einem Fehlschlag. Wird angezeigt statt
    -- verschluckt: eine Oberflaeche, die "Fehler beim Abruf" sagt, waehrend der
    -- Anbieter "Zustimmung abgelaufen" gemeldet hat, kostet den Menschen davor
    -- eine halbe Stunde.
    fehlermeldung           text,

    -- Zustandswert des laufenden Autorisierungsvorgangs (OAuth-state).
    --
    -- Die Bindung an Zugang UND Benutzer ist eine Sicherheitsanforderung, kein
    -- Feld: ohne sie genuegt ein untergeschobener Link, um im Namen eines
    -- Angemeldeten einen fremden Bankzugang einzurichten. Geprueft werden alle
    -- drei Spalten plus der Benutzer in angelegt_von.
    zustand                 text,
    zustand_gueltig_bis     timestamptz,
    zustand_verbraucht      boolean      NOT NULL DEFAULT false,

    angelegt_von            uuid         NOT NULL REFERENCES benutzer (id) ON DELETE CASCADE,
    angelegt_am             timestamptz  NOT NULL DEFAULT now(),

    -- Autorisiert ohne Ablauf oder ohne Sitzung waere ein Zugang, der nutzbar
    -- aussieht und keiner ist. Die Datenbank laesst diesen Zustand gar nicht
    -- erst entstehen.
    CONSTRAINT bankzugang_autorisiert_vollstaendig
        CHECK (status <> 'AUTORISIERT' OR (gueltig_bis IS NOT NULL AND sitzung IS NOT NULL))
);

COMMENT ON TABLE bankzugang IS
    'Autorisierte Zugaenge zu Instituten. Vorgang mit Status und Ablauf, siehe V3.';

-- Der Zustandswert wird bei jeder Rueckleitung nachgeschlagen. Eindeutig, weil
-- zwei Vorgaenge mit demselben Wert die Zuordnung mehrdeutig machten - und
-- Mehrdeutigkeit ist bei einer Sicherheitspruefung dasselbe wie keine Pruefung.
CREATE UNIQUE INDEX bankzugang_zustand_idx ON bankzugang (zustand) WHERE zustand IS NOT NULL;

CREATE INDEX bankzugang_benutzer_idx ON bankzugang (angelegt_von);


-- -----------------------------------------------------------------------------
-- Externes Konto
--
-- Der Schluessel ist die stabile Kennung des Anbieters (bei Enable Banking
-- identification_hash), NIEMALS die uid.
--
-- Die uid ist laut Dokumentation nur gueltig, solange die Sitzung autorisiert
-- ist. Wer sie persistiert, baut eine Datenbank, die nach dem ersten
-- Sitzungsablauf auf tote Kennungen zeigt - und der Fehler tritt erst Monate
-- spaeter auf, wenn niemand mehr an die Einrichtung denkt. Deshalb gibt es hier
-- keine Spalte dafuer, auch keine ungenutzte.
--
-- Die Eindeutigkeit auf der Kennung ist zugleich die Zusage, dass eine zweite
-- Autorisierung desselben Kontos keinen zweiten Datensatz erzeugt.
-- -----------------------------------------------------------------------------
CREATE TABLE externes_konto (
    id                      uuid         PRIMARY KEY,
    bankzugang_id           uuid         NOT NULL REFERENCES bankzugang (id) ON DELETE CASCADE,

    kennung                 text         NOT NULL UNIQUE,

    iban                    text,
    waehrung                text         NOT NULL,
    kontoart                text,
    produktname             text,
    bezeichnung             text         NOT NULL,

    -- Zuordnung auf ein fachliches Konto aus V1. Bleibt in dieser Stufe leer und
    -- wird von Hand gesetzt. Sie zu raten wuerde spaeter Buchungen auf falsche
    -- Konten verteilen, und der Fehler faellt erst in einer Auswertung auf.
    konto_id                uuid         REFERENCES konto (id) ON DELETE SET NULL,

    angelegt_am             timestamptz  NOT NULL DEFAULT now(),
    aktualisiert_am         timestamptz  NOT NULL DEFAULT now()
);

COMMENT ON TABLE externes_konto IS
    'Konten, wie der Bankanbieter sie kennt. Schluessel ist die stabile Kennung, nie die Sitzungs-uid.';

COMMENT ON COLUMN externes_konto.kennung IS
    'Stabiler Schluessel ueber Sitzungen hinweg (identification_hash). Keine Sitzungskennung.';

CREATE INDEX externes_konto_zugang_idx ON externes_konto (bankzugang_id);
CREATE INDEX externes_konto_konto_idx  ON externes_konto (konto_id);


-- -----------------------------------------------------------------------------
-- Externer Saldo
--
-- Ein Konto hat nicht einen Saldo, sondern mehrere gleichzeitig - gebucht,
-- verfuegbar, vorgemerkt. Sie zu einem Wert zusammenzuziehen waere bequem und
-- falsch: der gebuchte Saldo beantwortet eine andere Frage als der verfuegbare,
-- und die Kennzahl "verfuegbar" dieses Systems ist noch einmal etwas anderes als
-- beide.
--
-- Der Abrufzeitpunkt gehoert zum Wert. Ein Saldo ohne ihn ist eine Zahl ohne
-- Aussage. Genau daran haengt auch, dass gespeicherte Salden einen Fehlschlag
-- ueberleben duerfen: sie werden nicht falsch, sie werden alt - und das ist
-- ablesbar.
--
-- Historie statt Ueberschreiben: jeder Abruf legt einen Datensatz an. Ein
-- Fehlschlag, der die letzten bekannten Salden loescht, ist derselbe
-- Datenverlust wie ein abgestuerzter Import, nur bequemer zu uebersehen.
-- -----------------------------------------------------------------------------
CREATE TABLE externer_saldo (
    id                      uuid         PRIMARY KEY,
    externes_konto_id       uuid         NOT NULL REFERENCES externes_konto (id) ON DELETE CASCADE,

    art                     text         NOT NULL
                                         CHECK (art IN ('GEBUCHT', 'VERFUEGBAR', 'VORGEMERKT',
                                                        'ABSCHLUSS', 'SONSTIGE')),

    -- Der unveraenderte Code des Anbieters, auch bei bekannter Art. Eine
    -- Zuordnung, die sich spaeter als falsch erweist, laesst sich damit
    -- nachvollziehen statt geraten.
    art_original            text         NOT NULL,

    betrag                  numeric(15,2) NOT NULL,
    waehrung                text         NOT NULL,

    -- Stichtag laut Anbieter. Nicht jeder liefert ihn.
    referenzdatum           date,

    abgerufen_am            timestamptz  NOT NULL DEFAULT now()
);

COMMENT ON TABLE externer_saldo IS
    'Abgerufene Salden mit Abrufzeitpunkt. Historie, kein Ueberschreiben - siehe V3.';

CREATE INDEX externer_saldo_konto_idx ON externer_saldo (externes_konto_id, abgerufen_am DESC);


-- =============================================================================
-- Zugriffskontrolle
--
-- ENABLE und FORCE sind beide noetig. ENABLE allein nimmt den Tabelleneigentuemer
-- von saemtlichen Policies aus; laeuft die Anwendung versehentlich unter dieser
-- Rolle, ist die Zugriffskontrolle stillschweigend wirkungslos.
-- =============================================================================
ALTER TABLE bankzugang      ENABLE ROW LEVEL SECURITY;
ALTER TABLE bankzugang      FORCE  ROW LEVEL SECURITY;
ALTER TABLE externes_konto  ENABLE ROW LEVEL SECURITY;
ALTER TABLE externes_konto  FORCE  ROW LEVEL SECURITY;
ALTER TABLE externer_saldo  ENABLE ROW LEVEL SECURITY;
ALTER TABLE externer_saldo  FORCE  ROW LEVEL SECURITY;


-- -----------------------------------------------------------------------------
-- Sichtbar fuer jeden angemeldeten Benutzer - und das braucht eine Begruendung.
--
-- Bankzugaenge und externe Konten haengen NICHT an kontozugriff: die Zuordnung
-- auf ein fachliches Konto ist optional und in dieser Stufe leer. Eine Policy
-- ueber konto_lesbar() wuerde alles unsichtbar machen, solange sie fehlt - also
-- immer.
--
-- Haushaltsweit ist auch fachlich richtig: HB-05 stellt fest, dass es innerhalb
-- der Ehe keinen Geheimhaltungsbedarf gibt. Wer welchen Bankzugang eingerichtet
-- hat, steht in angelegt_von und ist damit nachvollziehbar, ohne die Sicht zu
-- teilen.
--
-- Ohne Benutzerkontext bleibt trotzdem alles unsichtbar. Fail-Closed gilt auch
-- fuer haushaltsweite Daten, sonst ist die Regel nicht mehr einheitlich pruefbar
-- - und genau das prueft RlsPolicyVollstaendigkeitTest.
-- -----------------------------------------------------------------------------
CREATE POLICY bankzugang_haushaltsweit ON bankzugang
    FOR ALL
    USING      (aktueller_benutzer() IS NOT NULL)
    WITH CHECK (aktueller_benutzer() IS NOT NULL);

CREATE POLICY externes_konto_haushaltsweit ON externes_konto
    FOR ALL
    USING      (aktueller_benutzer() IS NOT NULL)
    WITH CHECK (aktueller_benutzer() IS NOT NULL);

CREATE POLICY externer_saldo_haushaltsweit ON externer_saldo
    FOR ALL
    USING      (aktueller_benutzer() IS NOT NULL)
    WITH CHECK (aktueller_benutzer() IS NOT NULL);


-- -----------------------------------------------------------------------------
-- Rechte
--
-- ALTER DEFAULT PRIVILEGES aus V1 deckt kuenftige Tabellen ab, gilt aber nur fuer
-- den Rollen-Kontext, in dem es gesetzt wurde. Der explizite GRANT hier ist die
-- Absicherung dagegen, dass die Migration unter einer anderen Rolle laeuft als
-- V1 - dann greift die Voreinstellung naemlich nicht, und die Anwendung saehe
-- die neuen Tabellen gar nicht.
-- -----------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE, DELETE
    ON bankzugang, externes_konto, externer_saldo
    TO haushaltsbuch_app;
