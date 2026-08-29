package dev.michelelops.arcaniaquest.gioco

/** Un rettangolo sullo schermo, con l'origine in basso a sinistra come vuole libGDX. */
data class Riq(val x: Float, val y: Float, val w: Float, val h: Float) {
    val x1: Float get() = x + w
    val y1: Float get() = y + h
    val cx: Float get() = x + w / 2f
    val cy: Float get() = y + h / 2f

    fun ritira(quanto: Float) = Riq(x + quanto, y + quanto, w - quanto * 2f, h - quanto * 2f)

    /** La fetta orizzontale da [da] a [a], in frazione della larghezza. */
    fun fetta(da: Float, a: Float, gap: Float = 0f) =
        Riq(x + w * da + (if (da > 0f) gap / 2f else 0f), y,
            w * (a - da) - (if (da > 0f) gap / 2f else 0f) - (if (a < 1f) gap / 2f else 0f), h)
}

/**
 * Dove sta ogni cosa sullo schermo.
 *
 * La disposizione e' quella del bozzetto: la vista in alto a sinistra e
 * grande, lo zaino e la mappa in colonna a destra, e sotto la vista la
 * striscia con gruppo, diario e comandi. Si ricalcola tutto da larghezza
 * e altezza, cosi' la stessa interfaccia sta su una finestra e su un
 * telefono senza numeri scritti a mano da nessuna parte.
 */
class Telaio(val larghezza: Float, val altezza: Float) {

    val margine = (minOf(larghezza, altezza) * 0.014f).coerceIn(6f, 16f)
    private val gap = margine * 0.8f

    /** In verticale i pannelli si impilano: sotto una certa larghezza non ci starebbero affiancati. */
    val stretto = larghezza < altezza * 1.15f

    val vista: Riq
    val zaino: Riq
    val mappa: Riq
    val gruppo: Riq
    val diario: Riq
    val comandi: Riq

    init {
        if (!stretto) {
            val colonnaDestra = (larghezza * 0.27f).coerceIn(210f, 420f)
            val sinistra = larghezza - margine * 2f - colonnaDestra - gap
            val bassa = (altezza * 0.30f).coerceIn(130f, 260f)
            val xd = margine + sinistra + gap

            vista = Riq(margine, margine + bassa + gap, sinistra, altezza - margine * 2f - bassa - gap)
            val altoZaino = (altezza - margine * 2f - gap) * 0.44f
            zaino = Riq(xd, altezza - margine - altoZaino, colonnaDestra, altoZaino)
            mappa = Riq(xd, margine, colonnaDestra, altezza - margine * 2f - altoZaino - gap)

            val striscia = Riq(margine, margine, sinistra, bassa)
            gruppo = striscia.fetta(0f, 0.36f, gap)
            diario = striscia.fetta(0.36f, 0.70f, gap)
            comandi = striscia.fetta(0.70f, 1f, gap)
        } else {
            // Telefono in piedi: vista sopra, comandi grandi sotto, il resto
            // sta nella mappa a tutto schermo e nelle schede.
            val bassa = (altezza * 0.26f).coerceIn(140f, 320f)
            val mezzo = (altezza * 0.16f).coerceIn(90f, 200f)
            vista = Riq(margine, margine + bassa + mezzo + gap * 2f, larghezza - margine * 2f,
                altezza - margine * 2f - bassa - mezzo - gap * 2f)
            val fascia = Riq(margine, margine + bassa + gap, larghezza - margine * 2f, mezzo)
            gruppo = fascia.fetta(0f, 0.55f, gap)
            diario = fascia.fetta(0.55f, 1f, gap)
            val sotto = Riq(margine, margine, larghezza - margine * 2f, bassa)
            comandi = sotto.fetta(0f, 0.58f, gap)
            zaino = sotto.fetta(0.58f, 0.79f, gap)
            mappa = sotto.fetta(0.79f, 1f, gap)
        }
    }
}
