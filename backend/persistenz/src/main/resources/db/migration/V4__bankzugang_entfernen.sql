-- =============================================================================
-- V4 - Einen Bankzugang wieder loswerden
--
-- V3 legte den Zugangsbezug eines externen Kontos als NOT NULL mit ON DELETE
-- CASCADE an. Das war die richtige Annahme fuer ein System, das Zugaenge nur
-- einrichtet: solange nichts geloescht wird, ist CASCADE ein Sicherheitsnetz
-- gegen verwaiste Zeilen.
--
-- Sobald ein Zugang entfernbar wird, kehrt sich das um. CASCADE hiesse dann:
-- wer einen Zugang entfernt, verliert jeden je abgerufenen Saldo dieses
-- Instituts. Genau dagegen argumentiert V3 an zwei Stellen selbst - bei
-- externer_saldo ("ein Fehlschlag, der die letzten bekannten Salden loescht,
-- ist derselbe Datenverlust wie ein abgestuerzter Import") und im Status
-- FEHLGESCHLAGEN. Ein Saldo von vor drei Monaten laesst sich nicht neu
-- abrufen; die Autorisierung laesst sich jederzeit neu erteilen. Von beiden
-- ist der Saldo das Unersetzliche.
--
-- Deshalb: der Bezug wird optional, der Fremdschluessel steht auf SET NULL.
-- Konten und Salden ueberleben das Entfernen ihres Zugangs und stehen danach
-- fuer sich. Wer sie mitentfernen will, tut das ausdruecklich - die Anwendung
-- loescht sie dann als eigenen Schritt, bevor sie den Zugang entfernt.
--
-- Zur Sichtbarkeit: die Policy externes_konto_haushaltsweit aus V3 prueft
-- ausschliesslich aktueller_benutzer() IS NOT NULL und joint NICHT ueber
-- bankzugang. Ein geloester Bezug macht die Konten also nicht unsichtbar. Waere
-- die Policy ueber den Zugang formuliert, waeren die behaltenen Zeilen nach
-- diesem Schritt fuer niemanden mehr lesbar - vorhanden, aber unerreichbar.
-- =============================================================================

ALTER TABLE externes_konto ALTER COLUMN bankzugang_id DROP NOT NULL;

-- Der Name ist der von Postgres vergebene Standardname aus V3, wo die Spalte
-- inline mit REFERENCES definiert wurde. Bewusst ohne IF EXISTS: traefe der
-- Name nicht zu, bliebe der alte CASCADE-Constraint still stehen, und das
-- Entfernen eines Zugangs naehme die Salden mit, ohne dass es jemand merkt. Ein
-- Abbruch der Migration ist an dieser Stelle das gutmuetigere Verhalten.
ALTER TABLE externes_konto DROP CONSTRAINT externes_konto_bankzugang_id_fkey;

ALTER TABLE externes_konto
    ADD CONSTRAINT externes_konto_bankzugang_id_fkey
    FOREIGN KEY (bankzugang_id) REFERENCES bankzugang (id) ON DELETE SET NULL;

COMMENT ON COLUMN externes_konto.bankzugang_id IS
    'Zugang, ueber den das Konto bekannt wurde. NULL, wenn er entfernt wurde - die Zahlen bleiben.';
