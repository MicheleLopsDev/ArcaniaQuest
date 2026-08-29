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

    /** Quanto dura un passo e quanto una svolta, in secondi. */
    const val DURATA_PASSO = 0.22f
    const val DURATA_VOLTA = 0.20f

    /** Il buio comincia a mangiarsi le cose dopo questa distanza. */
    const val INIZIO_BUIO = 4f
    const val FONDO_BUIO = 26f

    /**
     * Forza delle luci puntiformi. libGDX le smorza con 1/(1 + d^2) e
     * satura a 2: sopra il 5 e' gia' tutto bruciato, non piu' luminoso.
     */
    const val FORZA_TORCIA_GRUPPO = 4.6f
    const val FORZA_TORCIA_A_MURO = 5.0f

    /** Quanto e' fitto il contorno di un poligono arrotondato. */
    const val PASSO_CONTORNO = 0.22f
}
