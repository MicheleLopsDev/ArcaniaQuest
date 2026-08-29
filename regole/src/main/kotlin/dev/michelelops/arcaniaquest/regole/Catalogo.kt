package dev.michelelops.arcaniaquest.regole

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Tutti i pezzi da cui si monta un dungeon.
 *
 * Il catalogo non sa da dove arriva il testo: glielo passa chi lo carica,
 * che su desktop e' un file e su Android un asset. Cosi' questo modulo
 * resta senza dipendenze da nessuna piattaforma.
 */
@Serializable
data class Catalogo(
    val versione: Int,
    val nota: String = "",
    val casellaMetri: Double,
    val altezzaMuriMetri: Double,
    val regoleFamiglia: Map<Famiglia, RegoleFamiglia>,
    val iniziali: List<Modulo>,
    val corridoi: List<Modulo>,
    val stanze: List<Modulo>
) {
    /** Tutti i moduli, iniziali per primi, poi corridoi, poi stanze. */
    val tutti: List<Modulo> by lazy { iniziali + corridoi + stanze }

    private val perId: Map<String, Modulo> by lazy { tutti.associateBy { it.id } }

    operator fun get(id: String): Modulo =
        perId[id] ?: error("Nessun modulo con id $id")

    fun regoleDi(m: Modulo): RegoleFamiglia =
        regoleFamiglia[m.famiglia] ?: error("Manca la regola per la famiglia ${m.famiglia}")

    /** I moduli d66, cioe' tutto quello che non e' il pezzo di partenza. */
    val pescabili: List<Modulo> by lazy { corridoi + stanze }

    /**
     * Il modulo iniziale che esce con un [tiro] di d6.
     * Fallisce forte: un catalogo senza il pezzo pescato e' rotto, e
     * scoprirlo a meta' partita sarebbe molto peggio.
     */
    fun iniziale(tiro: Int): Modulo =
        iniziali.firstOrNull { it.pesca.valore == tiro }
            ?: error("Nessun modulo iniziale per d6 = $tiro")

    /**
     * Il modulo che esce con un tiro di d66: due dadi letti in fila,
     * il primo le decine. [primo] e [secondo] vanno da 1 a 6.
     */
    fun d66(primo: Int, secondo: Int): Modulo {
        require(primo in 1..6 && secondo in 1..6) { "d66 fuori scala: $primo$secondo" }
        val valore = primo * 10 + secondo
        return pescabili.firstOrNull { it.pesca.valore == valore }
            ?: error("Nessun modulo per d66 = $valore")
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = false
        }

        fun daJson(testo: String): Catalogo = json.decodeFromString(serializer(), testo)
    }
}
