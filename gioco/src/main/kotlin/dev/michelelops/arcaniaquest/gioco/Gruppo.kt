package dev.michelelops.arcaniaquest.gioco

import dev.michelelops.arcaniaquest.regole.Dungeon
import dev.michelelops.arcaniaquest.regole.Lato
import dev.michelelops.arcaniaquest.regole.Ostacolo
import dev.michelelops.arcaniaquest.regole.Porta

/** Cosa puo' fare il gruppo. Sei mosse, non una di piu'. */
enum class Mossa { AVANTI, INDIETRO, PASSO_SINISTRO, PASSO_DESTRO, VOLTA_SINISTRA, VOLTA_DESTRA }

/** Perche' una mossa non e' andata a buon fine. */
enum class Rifiuto { NIENTE, ROCCIA, MURO, PORTA_CHIUSA, GIA_IN_MOVIMENTO }

/**
 * Il gruppo: una casella, un verso, e l'animazione che porta dall'una
 * all'altra.
 *
 * Tutto lo stato che conta sta in tre numeri — x, z e verso. Quello che
 * si vede a schermo durante il passo e' solo interpolazione: se si
 * spegnesse il gioco a meta' movimento, si riprenderebbe dalla casella
 * di arrivo senza perdere niente.
 */
class Gruppo(private val dungeon: Dungeon, x: Int, z: Int, verso: Lato) {

    var x: Int = x; private set
    var z: Int = z; private set
    var verso: Lato = verso; private set

    /** Ultimo rifiuto, per far dire qualcosa all'interfaccia. */
    var rifiuto: Rifiuto = Rifiuto.NIENTE; private set

    private var daX = x.toFloat(); private var daZ = z.toFloat()
    private var daAngolo = angoloDi(verso)
    private var aAngolo = daAngolo
    private var tempo = 0f
    private var durata = 0f

    val inMovimento: Boolean get() = tempo < durata

    /** Posizione mostrata: al centro della casella, o fra due. */
    val mostraX: Float get() = interpola(daX, x.toFloat())
    val mostraZ: Float get() = interpola(daZ, z.toFloat())
    val mostraAngolo: Float get() = interpola(daAngolo, aAngolo)

    fun avanza(delta: Float) {
        if (tempo < durata) tempo = minOf(durata, tempo + delta)
    }

    fun esegui(m: Mossa): Boolean {
        if (inMovimento) { rifiuto = Rifiuto.GIA_IN_MOVIMENTO; return false }
        rifiuto = Rifiuto.NIENTE
        return when (m) {
            Mossa.VOLTA_SINISTRA -> { volta(-1); true }
            Mossa.VOLTA_DESTRA -> { volta(1); true }
            Mossa.AVANTI -> passo(verso)
            Mossa.INDIETRO -> passo(verso.opposto)
            Mossa.PASSO_SINISTRO -> passo(verso.ruotato(-1))
            Mossa.PASSO_DESTRO -> passo(verso.ruotato(1))
        }
    }

    /**
     * Apre o chiude quello che c'e' davanti. Ritorna la porta solo se
     * qualcosa e' cambiato, cosi' chi chiama sa anche cosa dire.
     */
    fun agisci(): Porta? {
        if (inMovimento) return null
        return dungeon.commuta(x, z, verso)
    }

    private fun passo(dove: Lato): Boolean {
        when (dungeon.ostacolo(x, z, dove)) {
            Ostacolo.NIENTE -> {}
            Ostacolo.PORTA_CHIUSA -> { rifiuto = Rifiuto.PORTA_CHIUSA; return false }
            Ostacolo.MURO -> { rifiuto = Rifiuto.MURO; return false }
            Ostacolo.ROCCIA -> { rifiuto = Rifiuto.ROCCIA; return false }
        }
        daX = x.toFloat(); daZ = z.toFloat()
        x += dove.dx; z += dove.dz
        daAngolo = angoloDi(verso); aAngolo = daAngolo
        tempo = 0f; durata = Misure.DURATA_PASSO
        return true
    }

    private fun volta(quarti: Int) {
        daX = x.toFloat(); daZ = z.toFloat()
        daAngolo = angoloDi(verso)
        verso = verso.ruotato(quarti)
        // sempre per la via piu' corta: mai un giro di 270 gradi
        aAngolo = daAngolo - quarti * (Math.PI.toFloat() / 2f)
        tempo = 0f; durata = Misure.DURATA_VOLTA
    }

    private fun interpola(da: Float, a: Float): Float {
        if (durata <= 0f) return a
        val t = (tempo / durata).coerceIn(0f, 1f)
        val e = if (t < 0.5f) 2f * t * t else -1f + (4f - 2f * t) * t
        return da + (a - da) * e
    }

    companion object {
        /** Verso nord si guarda a z calante, cioe' indietro sull'asse. */
        fun angoloDi(l: Lato): Float = when (l) {
            Lato.NORD -> Math.PI.toFloat()
            Lato.EST -> Math.PI.toFloat() / 2f
            Lato.SUD -> 0f
            Lato.OVEST -> -Math.PI.toFloat() / 2f
        }

        fun dallaPartenza(d: Dungeon): Gruppo =
            Gruppo(d, d.partenza.x, d.partenza.z, d.versoIniziale)
    }
}
