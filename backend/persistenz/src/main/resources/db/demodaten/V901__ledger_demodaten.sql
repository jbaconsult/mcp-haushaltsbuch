-- =============================================================================
-- Synthetischer Demo-Datensatz fuer das Ledger
--
-- Ergaenzt V900 um die Kategorientaxonomie. Bewusst KEINE Buchungen: die legen
-- die Tests selbst an, damit sichtbar bleibt, welche Zeile zu welcher Zusage
-- gehoert. Ein Bestand aus dem Demo-Datensatz wuerde in jeder Zaehlung
-- mitlaufen und Tests unlesbar machen.
--
-- Auch keine weiteren Konten: die Kontenliste aus V900 ist Grundlage der
-- Zusagen in RlsZugriffTest.
--
-- Alle Werte erfunden. Zielbild ist Open Source - es gibt keinen Zeitpunkt, zu
-- dem echte Kategorien eines echten Haushalts hier stehen duerfen.
--
-- Idempotent, weil dieses Skript im CI-Stapel per psql laeuft und mehrfach
-- laufen kann. Siehe Kopf von V900.
-- =============================================================================

INSERT INTO kategoriegruppe (id, bezeichnung, sortierung) VALUES
    ('20000000-0000-0000-0000-000000000001', 'Lebenshaltung', 10),
    ('20000000-0000-0000-0000-000000000002', 'Wohnen',        20),
    ('20000000-0000-0000-0000-000000000003', 'Einkommen',     30)
ON CONFLICT DO NOTHING;

INSERT INTO kategorie (id, gruppe_id, bezeichnung) VALUES
    ('30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'Lebensmittel'),
    ('30000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001', 'Drogerie'),
    ('30000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000002', 'Miete'),
    ('30000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000002', 'Nebenkosten'),
    ('30000000-0000-0000-0000-000000000005', '20000000-0000-0000-0000-000000000003', 'Honorar')
ON CONFLICT DO NOTHING;
