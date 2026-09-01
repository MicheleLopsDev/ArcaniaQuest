package dev.michelelops.arcaniaquest.gioco

/**
 * Le misure del mondo, in metri. Una casella e' larga quanto i muri sono
 * alti: e' la proporzione che fa sembrare un corridoio un corridoio.
 */
object Misure {
    const val CASELLA = 3f
    const val ALTEZZA_MURO = 3f
    const val SPESSORE_MURO = 0.75f
    const val ALTEZZA_OCCHI = 1.65f

    /**
     * Campo visivo verticale, in gradi.
     *
     * Sessantaquattro erano troppi: su un riquadro largo diventano quasi
     * novantacinque gradi in orizzontale, e ai bordi si guardava di sbieco
     * dentro i muri. Cinquantadue tengono la stanza leggibile senza che
     * gli angoli si sfaldino.
     */
    const val CAMPO_VISIVO = 52f

    /** Quanto dura un passo e quanto una svolta, in secondi. */
    const val DURATA_PASSO = 0.22f
    const val DURATA_VOLTA = 0.20f

    /** Oltre questa distanza il buio si e' mangiato tutto: non si disegna piu'. */
    const val FONDO_BUIO = 26f

    /**
     * A che altezza arde la fiamma di una torcia appesa al muro: poco
     * sopra la testa, cosi' illumina e non abbaglia.
     */
    const val ALTEZZA_TORCIA = 1.95f

    /**
     * Quante torce a muro restano accese per volta.
     *
     * Lo shader di serie di libGDX ne accetta cinque in tutto e le
     * eccedenti le butta via senza dire niente: una e' quella del gruppo,
     * quindi per i muri ne restano quattro. Si tengono le piu' vicine, che
     * tanto le altre sono oltre il fondo del buio.
     */
    const val TORCE_ACCESE = 4

    /**
     * Forza delle luci puntiformi. libGDX le smorza con 1/(1 + d^2) e
     * satura a 2. Con le texture al posto delle tinte piatte i valori
     * sono raddoppiati: una pietra fotografata riflette meno della meta'
     * di un grigio pieno, e con la vecchia taratura il sotterraneo era
     * nero.
     */
    const val FORZA_TORCIA_GRUPPO = 9.5f
    const val FORZA_TORCIA_A_MURO = 10f

    /** Quanto e' fitto il contorno di un poligono arrotondato. */
    const val PASSO_CONTORNO = 0.22f
}
