-- =============================================================================
-- Synthetischer Demo-Datensatz
--
-- Wird NUR in den Profilen dev und test geladen, gesteuert ueber
-- quarkus.flyway.locations. Siehe application.properties.
--
-- Alle Werte sind erfunden. Zielbild ist Open Source: es gibt keinen Zeitpunkt,
-- zu dem echte IBANs, Kontonamen oder Betraege in dieser Datei stehen duerfen.
-- Ein einmal committeter echter Kontostand bleibt auch nach "git rm" in der
-- Historie.
--
-- Feste UUIDs, damit Tests dagegen schreiben koennen, ohne sie erst zu suchen.
-- =============================================================================

INSERT INTO benutzer (id, anzeigename) VALUES
    ('00000000-0000-0000-0000-000000000001', 'Demo Eins'),
    ('00000000-0000-0000-0000-000000000002', 'Demo Zwei');

INSERT INTO benutzeridentitaet (oidc_subjekt, benutzer_id) VALUES
    -- Dev Mode und Tests: dort laeuft die Anwendung ohne OIDC, das Subjekt kommt
    -- aus haushaltsbuch.entwicklung.benutzer-subjekt.
    ('demo-benutzer-eins', '00000000-0000-0000-0000-000000000001'),
    ('demo-benutzer-zwei', '00000000-0000-0000-0000-000000000002'),
    -- CI-Stack: dort ist das Subjekt die Keycloak-Benutzerkennung aus
    -- infra/keycloak/realm-haushaltsbuch-ci.json. Die IDs sind dort fest
    -- vergeben, damit diese Zuordnung ueberhaupt vorab schreibbar ist.
    ('aaaaaaaa-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001'),
    ('aaaaaaaa-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002');

INSERT INTO konto (id, bezeichnung, art, sphaere) VALUES
    ('10000000-0000-0000-0000-000000000001', 'Haushalt gemeinsam',  'HAUSHALTSKONTO',  'PRIVAT'),
    ('10000000-0000-0000-0000-000000000002', 'Giro Demo Eins',      'GIROKONTO',       'PRIVAT'),
    ('10000000-0000-0000-0000-000000000003', 'Geschaeft Demo Eins', 'GESCHAEFTSKONTO', 'FREIBERUFLICH'),
    ('10000000-0000-0000-0000-000000000004', 'Ruecklage',           'RUECKLAGENKONTO', 'PRIVAT'),
    -- Gehoert ausschliesslich Demo Zwei. Der Nachweis, dass die Zugriffskontrolle
    -- greift, haengt an genau diesem Konto: Demo Eins darf es nicht sehen.
    ('10000000-0000-0000-0000-000000000005', 'Giro Demo Zwei',      'GIROKONTO',       'PRIVAT');

INSERT INTO kontozugriff (benutzer_id, konto_id, recht) VALUES
    -- Demo Eins: gemeinsames Konto plus eigene Konten
    ('00000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'SCHREIBEN'),
    ('00000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002', 'SCHREIBEN'),
    ('00000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000003', 'SCHREIBEN'),
    ('00000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000004', 'SCHREIBEN'),
    -- Demo Zwei: gemeinsames Konto nur lesend, eigenes Konto schreibend.
    -- Das bildet den Fall ab, um den es geht - mitsehen duerfen, ohne alles zu sehen.
    ('00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'LESEN'),
    ('00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000005', 'SCHREIBEN');
