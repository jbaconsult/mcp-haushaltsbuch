# Glossar

Die Fachbegriffe dieses Projekts. Sie werden **nicht übersetzt** — im Code stehen sie
deutsch, mit ausgeschriebenen Umlauten.

Die verbindliche Fassung der Begriffe, die an einer ratifizierten Entscheidung hängen,
steht im Kumbuka-Scope `haushaltsbuch`. Dieses Dokument ist die ausformulierte Fassung.

---

## Kennzahlen

### verfuegbar

Die zentrale Kennzahl des Systems — **nicht** der Kontostand.

```
verfuegbar(Geschaeftskonto) =   Saldo
                              − offene USt-Zahllast
                              − ESt-/KiSt-Ruecklage bis zum naechsten Termin
                              − geschaeftlicher Ruecklagetopf
                              − Fixkosten bis zum naechsten gesicherten Zahlungseingang
```

Wird **deterministisch im System** berechnet, niemals vom Sprachmodell geschätzt. Die
Frage „geht das oder nicht?" wird ausschließlich aus dieser Zahl beantwortet.

**Code:** `Betrag verfuegbar(KontoId konto, LocalDate stichtag)` in `kern`.

### Zahllast

Die an das Finanzamt abzuführende Umsatzsteuer. Die Umsatzsteuer ist ein **durchlaufender
Posten** und niemals verfügbare Liquidität — sie liegt physisch auf dem Geschäftskonto und
wird dort als Verbindlichkeit geführt, nicht als Guthaben.

Wer sie als Guthaben zählt, hält sich für liquider als er ist, und zwar genau bis zum
10. des Folgemonats.

---

## Töpfe (HB-03)

Ein **Topf** ist eine virtuelle Zweckbindung auf einem physischen Rücklagenkonto. Töpfe
sind keine eigenen Konten.

### Nullsummen-Invariante

```
Saldo(Ruecklagenkonto) − Summe der Ist-Staende aller Toepfe = 0
```

Maschinell geprüft. Die Differenz landet in einem ausdrücklich sichtbaren Topf **„nicht
zugewiesen"** — nicht in einer stillen Restgröße. Ein unsichtbarer Rest ist der Ort, an dem
Fehler jahrelang überleben.

### Topfarten

| Art | Parameter | Verhalten |
|---|---|---|
| **Zielsparen** | Betrag + Zieldatum | Ergibt Sollrate und Soll-Stand heute |
| **Zyklisch** | Betrag + Turnus | Wird geleert und beginnt sofort neu |
| **Dauerruecklage** | Rate + Mindestbestand | Kein Zielbetrag |

### Verbindlichkeit vs. Sparziel

Die Trennung ist strikt und im UI sichtbar:

| | Verbindlichkeiten | Sparziele |
|---|---|---|
| Priorität | 0 — nicht verhandelbar | verhandelbar |
| Darf stocken | nein | ja |
| Darstellung | **nicht** als Sparziel | als Sparziel |
| Beispiele | USt-Zahllast, ESt-/KiSt-Vorauszahlung, Bausparvertrag mit Ansparverpflichtung | Haus, Festival/Freizeit/Urlaub, Reparaturen |

Der Bausparvertrag steht bei den Verbindlichkeiten, obwohl er wie Sparen aussieht: er löst
einen Kredit ab und trägt eine Ansparverpflichtung. Ein Sparziel, das man aussetzen kann,
ist etwas kategorial anderes.

**Befüllung** ist einkommensgetrieben und wird von einem Menschen ratifiziert. Der
Wochenabgleich prüft und meldet — er bucht nicht.

---

## Sphären

Drei strikt getrennte Bereiche, gekoppelt über **genau zwei** Kanten:

```
   privat/gemeinsam ◄── Privatentnahme ── freiberuflich
                                              │
                                       Steuerruecklage
                                              │
                                              ▼
                                        Finanzamt
```

| Sphäre | System of Record |
|---|---|
| privat/gemeinsam | **Haushaltsbuch** |
| freiberuflich | **Lexware Office** (GoBD-relevant) |
| Finanzamt | — Verbindlichkeiten, aus beiden gespeist |

Paperless ist Eingangskanal und privates Belegarchiv — **kein** GoBD-konformes Archiv.

Wichtig und leicht misszuverstehen: **Betriebsausgaben, die privat gezahlt werden, sind
steuerlich weiterhin abzugsfähig.** Nicht das Zahlungskonto entscheidet über die Zuordnung,
sondern Rechnungsempfänger und betriebliche Veranlassung.

Bewusstes Vermischen der Sphären ist ausgeschlossen — es zerstört die Messbarkeit von
`verfuegbar`.

---

## Klassifikation

### MREF — Mandatsreferenz

Kennzeichnet ein SEPA-Lastschriftmandat. Zusammen mit IBAN und CRED die **einzige**
zulässige Grundlage für die Klassifikation.

### CRED — Gläubigerkennung (Creditor Identifier)

Identifiziert den Lastschrifteinreicher.

### Dauermandat vs. POS-Lastschrift

| | Dauermandat | POS-Lastschrift (ELV) |
|---|---|---|
| Mandatsreferenz | **stabil** über Monate | bei jedem Einkauf **neu** |
| Gläubigerkennung | des Gläubigers | des Zahlungsdienstleisters |

**Erkennungsregel:** Eine Gläubigerkennung mit **mehr als drei** verschiedenen
Mandatsreferenzen im Bestand gehört einem Acquirer. Ihre Buchungen sind keine Mandate.

Belegte Acquirer: PAYONE (38 MREFs), Concardis/Nexi, SumUp, Adyen. Kartenzahlungen
(girocard) sind grundsätzlich keine Mandate.

Ohne diese Regel landen Einzelkäufe bei Lidl und Hornbach als „Jahreszahler" in der
Fixkostenliste — passiert im ersten Auswertungslauf 08/2026.

### Warum keine Namensmuster

Transaktionen werden **nie** über Textmuster im Gegenpartei-Namen klassifiziert. Zwei
belegte Fälle, beide Pflicht-Testfälle jeder Klassifikationsschicht:

1. **Darlehensrate 600 EUR/Monat** — die Gegenpartei lautet auf beide Ehepartner und wurde
   als konzerninterne Umbuchung weggefiltert.
2. **ESt-Erstattung 2024 über 4.779,40 EUR** — die Bank schreibt `ERSTATT. EINK.ST 2024`
   und `FA GERA`; der Filter suchte nach „Einkommensteuer" und „Finanzamt".

Namenstext darf **nur als letzte Stufe** nach IBAN und MREF greifen, und nie als einziges
Kriterium für einen Ausschluss.

### Kartensammelabbuchung

Sammelabbuchung und Karteneinzelumsatz beschreiben **denselben** Geldfluss. Genau eine
Seite darf in Auswertungen zählen — sonst Doppelzählung.

---

## silent cash drift

Geldverlust **ohne bewusste Entscheidung**. Abzugrenzen von Verschwendung: es geht nicht
darum, ob eine Ausgabe sinnvoll war, sondern ob sie entschieden wurde.

| Klasse | Erkennungsmerkmal |
|---|---|
| **Zombie-Abos** | Betragsserie in festem Intervall ohne Verlängerungsentscheidung |
| **Preisgleitung** | Betragssprung bei gleichbleibendem Empfänger |
| **Deckungsüberlappung** | doppelt abgesicherte Risiken |
| **Bargeldnebel** | Abhebungen ohne Kategorie |
| **Sphären-Leckage** | Betriebsausgabe privat gezahlt und nicht gebucht; oder private Ausgabe vom Geschäftskonto ohne Erfassung als Privatentnahme |

Sphären-Leckage ist die teuerste Klasse: jeder nicht geltend gemachte Euro kostet etwa
44 Cent Steuerwirkung.

**Auswertung nach Entscheidungscharakter** — feste Verpflichtung, wiederkehrend disponibel,
einmalig disponibel, Drift — nie nach gut/schlecht, und immer gegen die eigene Historie
statt gegen Haushaltsdurchschnitte.

---

## Importinvarianten

Jeder Import validiert sich selbst, sonst gilt er als **nicht erfolgt**.

| | Invariante |
|---|---|
| **I1** | Anfangssaldo plus Summe der Buchungen gleich Endsaldo, je Auszug bzw. Report |
| **I2** | Endsaldo Block N gleich Anfangssaldo Block N+1, je Konto |
| **I3** | Jede Buchung hat ihren Detailblock |
| **I4** | Deduplizierung über die Bankreferenz — Exportzeiträume überlappen an den Randtagen |
| **I5** | IBAN-Prüfsumme |

Was nicht aufgeht, landet in einer **Fehlerliste**, nicht im Datenbestand.

### Zwei MT940-Fallen

**Feld 61, das Zeichen nach C/D** ist das dritte Zeichen von „EUR", **nicht** die
Stornokennung. Storno steht davor als `RC`/`RD`.

**Das Buchungsdatum kommt nur als MMTT ohne Jahr** und muss aus der Valuta abgeleitet
werden. Am Jahreswechsel ist das kein Randfall, sondern der Normalfall.

I5 existiert, weil MT940-Zeilen bei etwa 55 Zeichen umbrechen — mitten in IBANs.
