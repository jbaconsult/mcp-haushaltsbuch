-- =============================================================================
-- V1 Grundschema mit zeilenbasierter Zugriffskontrolle
--
-- Umfang bewusst klein: Benutzer, Konto, Kontozugriff. Genau so viel, wie die
-- Zugriffskontrolle zum Nachweis braucht. Buchungen, Toepfe und die Berechnung
-- von "verfuegbar" sind eine eigene Entscheidung (offener Punkt Ledger-Kern),
-- keine Nebenwirkung eines Scaffoldings.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- Anwendungsrolle
--
-- Wird ohne LOGIN und ohne Passwort angelegt. Zugangsdaten werden ausserhalb
-- gesetzt (Compose-Init bzw. Betriebs-Runbook) - ein Passwort in einer Migration
-- landet in der Git-Historie und ist dort nicht mehr zu entfernen.
--
-- Ohne LOGIN funktioniert "SET ROLE haushaltsbuch_app" weiterhin, was fuer Tests
-- ausreicht.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'haushaltsbuch_app') THEN
        CREATE ROLE haushaltsbuch_app NOLOGIN;
    END IF;
END
$$;


-- -----------------------------------------------------------------------------
-- Benutzerkontext
--
-- Liest die Sitzungsvariable, die die Anwendung pro Transaktion setzt. Der zweite
-- Parameter "true" bedeutet: nicht mit einem Fehler abbrechen, wenn die Variable
-- fehlt, sondern NULL liefern.
--
-- NULL ist hier die entscheidende Eigenschaft. Ist kein Kontext gesetzt, trifft
-- keine Policy auf irgendeine Zeile zu - wer vergisst, den Kontext zu setzen,
-- sieht nichts. Fail-Closed. Die umgekehrte Voreinstellung waere ein Datenleck,
-- das niemandem auffaellt.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION aktueller_benutzer() RETURNS uuid
    LANGUAGE sql
    STABLE
AS $$
    SELECT NULLIF(current_setting('app.benutzer_id', true), '')::uuid
$$;

COMMENT ON FUNCTION aktueller_benutzer() IS
    'Benutzer der laufenden Transaktion aus app.benutzer_id. NULL, wenn nicht gesetzt.';


-- -----------------------------------------------------------------------------
-- Tabellen
-- -----------------------------------------------------------------------------
CREATE TABLE benutzer (
    id              uuid         PRIMARY KEY,
    anzeigename     text         NOT NULL,
    angelegt_am     timestamptz  NOT NULL DEFAULT now()
);

COMMENT ON TABLE benutzer IS 'Personen, die sich anmelden koennen.';

-- -----------------------------------------------------------------------------
-- Identitaetsaufloesung
--
-- Bewusst eine eigene Tabelle OHNE Zugriffskontrolle, und das braucht eine
-- Begruendung.
--
-- Die Anmeldung muss den OIDC-Subject auf den fachlichen Benutzer abbilden. Diese
-- Abfrage laeuft, BEVOR der Benutzerkontext feststeht - sie stellt ihn ja erst
-- fest. Laege sie auf einer RLS-geschuetzten Tabelle, wuerde die Policy den
-- Kontext voraussetzen, den die Abfrage gerade erst ermittelt. Ein Henne-Ei-
-- Problem, das sich mit Tricks in der WHERE-Klausel nur scheinbar loesen laesst:
-- Postgres garantiert keine Auswertungsreihenfolge zwischen Policy und
-- Bedingung.
--
-- Der Inhalt rechtfertigt die Ausnahme: zwei opake Kennungen, kein Name, kein
-- Kontobezug. Alles Schuetzenswerte steht in "benutzer" und bleibt geschuetzt.
-- -----------------------------------------------------------------------------
CREATE TABLE benutzeridentitaet (
    -- Subject-Claim aus dem OIDC-Token. Bewusst der Subject und nicht die
    -- E-Mail-Adresse: der Subject ist unveraenderlich, eine Adresse nicht.
    oidc_subjekt    text         PRIMARY KEY,
    benutzer_id     uuid         NOT NULL REFERENCES benutzer (id) ON DELETE CASCADE
);

COMMENT ON TABLE benutzeridentitaet IS
    'Abbildung OIDC-Subject auf Benutzer. Ohne RLS - siehe Begruendung in V1.';

-- Bewusst NICHT eindeutig: ein Benutzer darf mehrere Identitaeten haben. Genau
-- das ist der Normalfall, sobald derselbe Mensch gegen verschiedene Realms
-- arbeitet - auth.jbaconsult.com in der Entwicklung, auth.kumbuka.ai in
-- Produktion, ein lokaler Keycloak in der CI.
CREATE INDEX benutzeridentitaet_benutzer_idx ON benutzeridentitaet (benutzer_id);

CREATE TABLE konto (
    id              uuid         PRIMARY KEY,
    bezeichnung     text         NOT NULL,
    art             text         NOT NULL
                                 CHECK (art IN ('HAUSHALTSKONTO', 'GIROKONTO',
                                                'GESCHAEFTSKONTO', 'RUECKLAGENKONTO',
                                                'KREDITKONTO')),
    sphaere         text         NOT NULL
                                 CHECK (sphaere IN ('PRIVAT', 'FREIBERUFLICH', 'FINANZAMT')),
    angelegt_am     timestamptz  NOT NULL DEFAULT now()
);

-- Keine IBAN-Spalte, und das ist kein Versehen. Zielbild ist Open Source; IBANs
-- und Kontonamen kommen ausschliesslich aus Konfiguration. Wird spaeter eine
-- Bankverbindung gebraucht, gehoert sie in eine getrennte, verschluesselte
-- Tabelle - nicht neben die Bezeichnung.
COMMENT ON TABLE konto IS 'Konten. Ohne Bankverbindung, siehe Kommentar in V1.';

CREATE TABLE kontozugriff (
    benutzer_id     uuid         NOT NULL REFERENCES benutzer (id) ON DELETE CASCADE,
    konto_id        uuid         NOT NULL REFERENCES konto (id)    ON DELETE CASCADE,
    -- LESEN darf sehen, SCHREIBEN darf aendern. Bewusst grob gehalten; feinere
    -- Rechte kommen, wenn es dafuer einen Anwendungsfall gibt.
    recht           text         NOT NULL CHECK (recht IN ('LESEN', 'SCHREIBEN')),
    erteilt_am      timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (benutzer_id, konto_id)
);

COMMENT ON TABLE kontozugriff IS
    'Zuordnung Benutzer zu Konto. Grundlage saemtlicher RLS-Policies.';

CREATE INDEX kontozugriff_konto_idx ON kontozugriff (konto_id);


-- -----------------------------------------------------------------------------
-- Zugriffskontrolle
--
-- ENABLE und FORCE sind BEIDE noetig.
--
-- ENABLE allein schaltet die Policies ein, nimmt den Tabelleneigentuemer aber
-- davon aus. Laeuft die Anwendung versehentlich unter der Eigentuemerrolle, ist
-- die gesamte Zugriffskontrolle stillschweigend wirkungslos: nichts schlaegt
-- fehl, keine Warnung erscheint, und jeder sieht alles.
--
-- FORCE schliesst diese Luecke. Es ist der Unterschied zwischen einer
-- Zugriffskontrolle und dem Anschein einer Zugriffskontrolle.
-- -----------------------------------------------------------------------------
ALTER TABLE benutzer     ENABLE ROW LEVEL SECURITY;
ALTER TABLE benutzer     FORCE  ROW LEVEL SECURITY;
ALTER TABLE konto        ENABLE ROW LEVEL SECURITY;
ALTER TABLE konto        FORCE  ROW LEVEL SECURITY;
ALTER TABLE kontozugriff ENABLE ROW LEVEL SECURITY;
ALTER TABLE kontozugriff FORCE  ROW LEVEL SECURITY;

-- Ein Benutzer sieht sich selbst.
CREATE POLICY benutzer_eigener ON benutzer
    FOR ALL
    USING      (id = aktueller_benutzer())
    WITH CHECK (id = aktueller_benutzer());

-- Ein Konto ist sichtbar, wenn ein Zugriffseintrag dafuer existiert.
CREATE POLICY konto_sichtbar ON konto
    FOR SELECT
    USING (id IN (SELECT kz.konto_id
                    FROM kontozugriff kz
                   WHERE kz.benutzer_id = aktueller_benutzer()));

-- Aendern setzt SCHREIBEN voraus. Getrennt von SELECT, weil Lesen und Schreiben
-- unterschiedliche Rechte sind - ein gemeinsames FOR ALL wuerde beides
-- gleichsetzen.
CREATE POLICY konto_aenderbar ON konto
    FOR UPDATE
    USING      (id IN (SELECT kz.konto_id FROM kontozugriff kz
                        WHERE kz.benutzer_id = aktueller_benutzer()
                          AND kz.recht = 'SCHREIBEN'))
    WITH CHECK (id IN (SELECT kz.konto_id FROM kontozugriff kz
                        WHERE kz.benutzer_id = aktueller_benutzer()
                          AND kz.recht = 'SCHREIBEN'));

-- Ein Benutzer sieht seine eigenen Zugriffsrechte, nicht die anderer. Sonst
-- verriete die Rechteliste die Existenz fremder Konten - und damit genau das,
-- was die Zugriffskontrolle verbergen soll.
CREATE POLICY kontozugriff_eigener ON kontozugriff
    FOR SELECT
    USING (benutzer_id = aktueller_benutzer());


-- -----------------------------------------------------------------------------
-- Rechte der Anwendungsrolle
--
-- Bewusst kein Eigentum an den Tabellen und kein BYPASSRLS. Die Rolle bekommt
-- genau die Datenmanipulation, die sie braucht - und die Policies darunter.
-- -----------------------------------------------------------------------------
GRANT USAGE ON SCHEMA public TO haushaltsbuch_app;
GRANT SELECT, INSERT, UPDATE, DELETE
    ON benutzer, benutzeridentitaet, konto, kontozugriff
    TO haushaltsbuch_app;
GRANT EXECUTE ON FUNCTION aktueller_benutzer() TO haushaltsbuch_app;

-- Gilt fuer Tabellen, die kuenftige Migrationen anlegen. Ohne diese Zeile muss
-- jede neue Migration daran denken - und irgendwann denkt eine nicht daran.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO haushaltsbuch_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO haushaltsbuch_app;

-- Erlaubt dem migrierenden Benutzer, per SET ROLE in die Anwendungsrolle zu
-- wechseln. Das ist die Grundlage dafuer, dass die Anwendung ihre Rolle pro
-- Transaktion setzt statt sich darauf zu verlassen, dass die Verbindung schon
-- die richtige ist.
--
-- Warum das noetig ist: im Dev Mode und in Tests baut Quarkus die Verbindung
-- ueber Dev Services als Superuser auf, und ein SUPERUSER umgeht Row-Level-
-- Security IMMER - auch FORCE hilft dagegen nicht. Ohne Rollenwechsel waere die
-- Zugriffskontrolle in genau der Umgebung wirkungslos, in der man sie beim
-- Entwickeln bemerken wuerde.
--
-- Ein GRANT ist keine Rechteerweiterung: SET ROLE schraenkt ein.
DO $$
BEGIN
    EXECUTE format('GRANT haushaltsbuch_app TO %I', current_user);
EXCEPTION
    WHEN OTHERS THEN
        -- Superuser sind implizit Mitglied jeder Rolle; dort ist der GRANT
        -- ueberfluessig und darf nicht die Migration abbrechen.
        RAISE NOTICE 'GRANT haushaltsbuch_app TO % nicht noetig oder nicht moeglich: %',
            current_user, SQLERRM;
END
$$;
