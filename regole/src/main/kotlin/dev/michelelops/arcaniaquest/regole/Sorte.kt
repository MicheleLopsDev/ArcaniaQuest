package dev.michelelops.arcaniaquest.regole

import kotlin.random.Random

/**
 * I dadi della partita.
 *
 * Ogni sotterraneo nasce da un **seme**: lo stesso seme rifa' lo stesso
 * dungeon, casella per casella. Serve per tre cose molto pratiche —
 * rigiocare una partita andata bene, farsi raccontare da qualcuno dov'e'
 * finito, e soprattutto poter riprodurre un guaio invece di inseguirlo.
 *
 * Si usa `kotlin.random.Random` e non quello di Java: e' definito dalla
 * libreria standard di Kotlin e da' la stessa sequenza ovunque, quindi
 * un seme scritto su un telefono rifa' lo stesso dungeon sul desktop.
 */
class Sorte(val seme: Long) {

    private val dadi = Random(seme)

    fun d6(): Int = dadi.nextInt(6) + 1

    /** Due dadi letti in fila, il primo le decine: da 11 a 66. */
    fun d66(): Int = d6() * 10 + d6()

    fun fra(daInclusivo: Int, aInclusivo: Int): Int =
        dadi.nextInt(daInclusivo, aInclusivo + 1)

    fun <T> uno(fra: List<T>): T = fra[dadi.nextInt(fra.size)]

    /**
     * Il seme come lo si scrive e lo si detta: in base 36, cosi' sta in
     * poche lettere invece che in venti cifre.
     */
    fun semeScritto(): String = scrivi(seme)

    override fun toString(): String = "Sorte(${semeScritto()})"

    companion object {
        fun scrivi(seme: Long): String = seme.toString(36).uppercase()

        /**
         * Legge un seme come lo scriverebbe una persona. Accetta la forma
         * in base 36 (`K7X2M`) e anche un numero normale. Se il testo non
         * si lascia leggere, se ne fa una impronta invece di rifiutarlo:
         * qualunque parola diventa un seme valido, ed e' un modo comodo
         * per battezzare una partita.
         */
        fun leggi(testo: String): Sorte {
            val pulito = testo.trim()
            if (pulito.isEmpty()) return nuova()
            pulito.toLongOrNull(36)?.let { return Sorte(it) }
            return Sorte(pulito.fold(1125899906842597L) { h, c -> h * 31 + c.code })
        }

        /** Un seme nuovo, quando la partita comincia senza che sia stato scelto. */
        fun nuova(): Sorte = Sorte(Random.nextLong(1L, 78364164096L)) // fino a 7 cifre in base 36
    }
}
