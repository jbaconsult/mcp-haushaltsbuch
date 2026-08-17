# ADR-0008 — Finanzprognose als eigenes Modul

| | |
|---|---|
| Status | Angenommen |
| Datum | 2026-08-17 |
| Kumbuka | `decision.hb-08-modul-finanzprognose`, `constraint.prognose-nie-optimistischer` |
| Verhältnis | Setzt ADR-0003 voraus |

## Kontext

Der eigentliche Hebel dieses Systems ist nicht die rückblickende Buchhaltung, sondern die
Vorausschau. Der Grund liegt in der Einkommenslage: bei einem festen Gehalt ergibt sich
Planbarkeit von selbst. Bei freiberuflichen Einnahmen mit schwankender Höhe und schwankendem
Eingang, dazu Steuervorauszahlungen und Nachzahlungen aus der Veranlagung, muss Planbarkeit
hergestellt werden.

Die Analyse aus Phase 1 hat zwei Dinge gezeigt, die das Modul formen. Erstens hat sich der
laufende Bedarf binnen eines Jahres um deutlich mehr als die Hälfte erhöht, und ein einzelner
Monat wies ein Vielfaches des üblichen Zuflusses auf. Ein Mittelwert über solche Daten sieht
präzise aus und ist falsch. Zweitens war ein Rückläufer einer Lastschrift auf eine Deckungslücke
von weniger als einem Tag zurückzuführen — die Kennzahl muss also nicht nur stimmen, sie muss
auf den Tag stimmen.

## Entscheidung

**Ein eigenes Modul, getrennt vom Ledger.** Der Grund ist nicht Modularität: das Ledger ist
Vergangenheit und deterministisch, die Prognose ist Zukunft und annahmenbasiert. Vermischt man
beides, ist später nicht mehr entscheidbar, was gemessen und was geschätzt war. **Die Prognose
liest das Ledger und schreibt niemals hinein.**

### Drei Fragetypen, drei Rechnungen

Ein früherer Entwurf hatte ein einziges Werkzeug der Form „kann ich mir das leisten"
vorgesehen. Das warf drei verschiedene Rechnungen zusammen:

1. **Einmalkonsum.** Punktuell aus der Verfügbarkeitskennzahl, nur gesicherte Posten, Antwort
   in Sekundenbruchteilen. Das ist die Frage im Laden.
2. **Wiederkehrende Verpflichtung** — ein Abonnement, ein Leasingvertrag. Kategorial anders:
   nicht „ist heute Geld da", sondern „trägt der Haushalt diese Rate über die gesamte Laufzeit,
   auch über die Steuertermine hinweg". Das ist ein Prognoselauf, kein Kontostandsblick. Die
   Fehlerkosten sind asymmetrisch — ein Fehlkauf kostet den Kaufpreis, ein Vertrag über drei
   Jahre kostet drei Jahre.
3. **Szenario mit Nebenbedingung** — „ist ein Urlaub in diesem Zeitraum möglich, ohne die
   Steuerrücklage anzutasten". Baseline gegen Variante, mit benannten Annahmen.

### Bandbreite statt Punktwert

Variable Ausgaben gehen als **Verteilung** in die Prognose ein, nicht als Mittelwert. Die
Antwort ist eine Bandbreite: gesichert, erwartet, schlecht. Ein einzelner Wert über einem
Datenbestand mit dieser Streuung ist eine Scheingenauigkeit.

### Forderungen tragen eine Sicherheitsklasse

Zuflüsse sind nicht gleich sicher. Mindestens drei Klassen: Rechnung gestellt mit Zahlungsziel
(Betrag sicher, Termin unsicher), Leistung erbracht aber nicht fakturiert (Betrag sicher, Termin
selbst steuerbar), Vorhaben in Aussicht (spekulativ). Abflüsse spiegeln das — ein
Vorauszahlungstermin ist sicher, eine Nachzahlung aus der Veranlagung hat einen ungefähren
Termin und einen unbekannten Betrag.

Die Forderungsposition ist damit eine **dritte Kopplungskante** zwischen privater und
freiberuflicher Sphäre, neben Privatentnahme und Steuerrücklage. Sie ist qualitativ anders:
erwartetes Geld, das in keinem Kontoauszug erscheint, bis es als Gutschrift kommt — und dann mit
Rechnungsbezug nur unstrukturiert im Verwendungszweck.

### Die harte Regel: nie optimistischer

Zahlungstermine bekommen ein aus der Historie gelerntes Zuverlässigkeitsmerkmal; wer mehrfach
fristgerecht gezahlt hat, gilt als verlässlich, sonst wird ein konfigurierbarer Puffer
aufgeschlagen. Das Merkmal verfällt über ein rollierendes Fenster.

**Dieses Merkmal wirkt ausschließlich im Erwartungsniveau der Prognose.** Die
Verfügbarkeitskennzahl rechnet für jeden Schuldner mit Fälligkeit plus Puffer, unabhängig vom
Merkmal. Andernfalls macht eine falsche Verhaltensprognose die gesicherte Zahl optimistischer,
das System antwortet zustimmend, und eine Lastschrift platzt. Ein Fehlurteil darf Genauigkeit
kosten, nie Deckung.

Einschränkung, die im ersten Betriebsjahr gilt: die Zahl der belegten Schuldner ist einstellig
und klein. Das Merkmal hat zunächst keine Aussagekraft und darf entsprechend wenig Gewicht
bekommen.

## Alternativen

**Prognose im Ledger-Modul.** Verworfen, weil Annahmen und Buchungen dann in einer Schicht
liegen und die Unterscheidbarkeit verlorengeht — siehe oben.

**Ein Werkzeug für alle drei Fragetypen.** Verworfen: die Rechnungen unterscheiden sich in
Zeithorizont, Eingangsdaten und Fehlerkosten. Ein gemeinsames Werkzeug müsste diese Unterschiede
zur Laufzeit erraten.

**Mittelwerte statt Verteilungen**, weil einfacher und schneller. Verworfen: der vorhandene
Datenbestand widerlegt die Annahme der Stabilität unmittelbar.

## Konsequenzen

- Das Modul braucht als Eingaben: die Mandatsliste für Fixkosten, den Steuerkalender als harte
  Termine, Forderungen mit Sicherheitsklasse, und eine aus der Historie abgeleitete Verteilung
  variabler Ausgaben. Die ersten beiden liegen vor, die dritte ist neu.
- Der Steuerkalender ist damit **keine späte Ausbaustufe**. Die Software hat Zeit, der
  Steuerkalender nicht.
- **Akzeptanztest der ersten Ausbaustufe:** Das Modul muss beantworten, ob der nächste
  Vorauszahlungstermin gedeckt ist — auf einem Konto ohne Kreditlinie, dessen vorhandene
  Rücklage um Größenordnungen unter dem fälligen Betrag liegt. Beantwortet es das nicht, ist es
  Dekoration, unabhängig davon, wie gut die Szenariodarstellung aussieht.
