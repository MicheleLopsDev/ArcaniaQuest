package dev.michelelops.arcaniaquest.gioco

import dev.michelelops.arcaniaquest.regole.Lato
import dev.michelelops.arcaniaquest.regole.Modulo

/** Cosa puo' fare il gruppo. Sei mosse, non una di piu'. */
enum class Mossa { AVANTI, INDIETRO, PASSO_SINISTRO, PASSO_DESTRO, VOLTA_SINISTRA, VOLTA_DESTRA }

/** Perche' una mossa non e' andata a buon fine. */
enum class Rifiuto { NIENTE, ROCCIA, PORTA_CHIUSA, GIA_IN_MOVIMENTO }

/**
 * Il gruppo: una casella, un verso, e l'animazione che porta dall'una
 * all'altra.
 *
 * Tutto lo stato che conta sta in tre numeri — x, z e verso. Quello che
 * si vede a schermo durante il passo e' solo interpolazione: se si
 * spegnesse il gioco a meta' movimento, si riprenderebbe dalla casella
 * di arrivo senza perdere niente.
 */
class Gruppo(private val modulo: Modulo, x: Int, z: Int, verso: Lato) {

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

    private fun passo(dove: Lato): Boolean {
        val nx = x + dove.dx
        val nz = z + dove.dz
        if (!modulo.calpestabile(nx, nz)) {
            // se di la' c'e' un connettore, non e' roccia: e' un'uscita
            // che per ora non porta da nessuna parte
            val k = modulo.connettoreIn(x, z, dove)
            rifiuto = if (k != null) Rifiuto.PORTA_CHIUSA else Rifiuto.ROCCIA
            return false
        }
        daX = x.toFloat(); daZ = z.toFloat()
        x = nx; z = nz
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

        /**
         * Dove comincia il gruppo. Se il modulo dichiara una partenza si
         * usa quella; se non ce l'ha — le stanze e i corridoi non ce
         * l'hanno — si sceglie la casella piu' centrale e si guarda verso
         * la meta' con piu' spazio, cosi' non ci si ritrova col naso nel
         * muro appena entrati.
         */
        fun dallaPartenza(m: Modulo): Gruppo {
            m.partenza?.let { return Gruppo(m, it.x, it.z, it.verso) }

            val celle = m.celle()
            val cx = celle.sumOf { it.x }.toFloat() / celle.size
            val cz = celle.sumOf { it.z }.toFloat() / celle.size
            val centro = celle.minBy { (it.x - cx) * (it.x - cx) + (it.z - cz) * (it.z - cz) }

            // Si guarda dalla parte dove si vede piu' lontano.
            val verso = Lato.entries.maxBy { l ->
                var passi = 0
                var x = centro.x
                var z = centro.z
                while (m.calpestabile(x + l.dx, z + l.dz) && passi < 32) {
                    x += l.dx; z += l.dz; passi++
                }
                passi
            }
            return Gruppo(m, centro.x, centro.z, verso)
        }
    }
}
