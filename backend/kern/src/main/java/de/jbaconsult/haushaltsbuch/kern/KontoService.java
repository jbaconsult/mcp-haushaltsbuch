package de.jbaconsult.haushaltsbuch.kern;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Fachliche Operationen auf Konten.
 *
 * <p>Der einzige Rechenweg für kontobezogene Fragen. REST und MCP rufen beide hierher - zwei
 * Implementierungen derselben Kennzahl würden auseinanderlaufen, und der Fehler fiele erst beim
 * Jahresabschluss auf.
 */
@ApplicationScoped
public class KontoService {

    private final KontoPort kontoPort;

    @Inject
    public KontoService(KontoPort kontoPort) {
        this.kontoPort = kontoPort;
    }

    public List<Konto> sichtbareKonten() {
        return kontoPort.alleSichtbaren();
    }

    public Optional<Konto> konto(KontoId id) {
        return kontoPort.findeNachId(id);
    }

    /**
     * Konten einer Sphäre.
     *
     * <p>Die Sphärentrennung ist bindend: privat/gemeinsam, freiberuflich und Finanzamt sind
     * gekoppelt über genau zwei Kanten - Privatentnahme und Steuerrücklage. Eine Auswertung, die
     * über Sphärengrenzen summiert, ist fachlich falsch, auch wenn sie technisch funktioniert.
     */
    public List<Konto> kontenDerSphaere(Sphaere sphaere) {
        return kontoPort.alleSichtbaren().stream()
                .filter(konto -> konto.sphaere() == sphaere)
                .toList();
    }
}
