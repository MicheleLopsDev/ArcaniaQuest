package dev.michelelops.arcaniaquest.regole

import kotlinx.serialization.Serializable

/**
 * Un pezzo del dungeon.
 *
 * [caselle] e' l'unica cosa che serve al movimento: una riga per ogni z
 * (da nord a sud), un carattere per ogni x (da ovest a est), `1` dove si
 * cammina. Niente fisica, nessuna geometria di collisione: la domanda che
 * si fa il gioco e' sempre e solo «la casella accanto e' calpestabile?».
 */
@Serializable
data class Modulo(
    val id: String,
    val nome: String,
    val famiglia: Famiglia,
    val pesca: Pesca,
    val ingombro: Ingombro,
    val caselle: List<String>,
    val pianta: List<Forma> = emptyList(),
    val connettori: List<Connettore> = emptyList(),
    val partenza: Partenza? = null,
    val arredi: List<Arredo> = emptyList(),
    val verificato: Boolean = false
) {
    val larghezza: Int get() = ingombro.w
    val profondita: Int get() = ingombro.d

    fun dentro(x: Int, z: Int): Boolean =
        x >= 0 && z >= 0 && x < larghezza && z < profondita

    /** Ci si cammina. Fuori dal modulo la risposta e' sempre no. */
    fun calpestabile(x: Int, z: Int): Boolean =
        dentro(x, z) && caselle[z][x] == '1'

    fun calpestabile(c: Cella): Boolean = calpestabile(c.x, c.z)

    /** Tutte le caselle su cui si puo' stare, in ordine deterministico. */
    fun celle(): List<Cella> =
        (0 until profondita).flatMap { z ->
            (0 until larghezza).mapNotNull { x -> if (caselle[z][x] == '1') Cella(x, z) else null }
        }

    /**
     * Un connettore ha senso solo se di la' dal lato non c'e' un'altra
     * casella del modulo: altrimenti non e' un'uscita, e' un muro interno
     * che non dovrebbe nemmeno esistere.
     */
    fun apreVersoFuori(k: Connettore): Boolean =
        calpestabile(k.x, k.z) && !calpestabile(k.x + k.lato.dx, k.z + k.lato.dz)

    fun connettoreIn(x: Int, z: Int, lato: Lato): Connettore? =
        connettori.firstOrNull { it.x == x && it.z == z && it.lato == lato }

    /**
     * Il modulo ruotato di [quarti] scatti di 90 gradi in senso orario.
     *
     * Serve al generatore: pescato un pezzo, lo si gira finche' il suo
     * connettore libero non guarda il lato giusto. Ruota tutto insieme —
     * caselle, connettori, partenza, arredi e anche la pianta scritta a
     * mano — perche' un modulo ruotato a meta' e' un modulo rotto, e una
     * sala ovale che ruotando torna quadrata sarebbe il modo peggiore di
     * accorgersene.
     */
    fun ruotato(quarti: Int): Modulo {
        val q = ((quarti % 4) + 4) % 4
        if (q == 0) return this
        var m = this
        repeat(q) { m = m.ruotaDiUnQuarto() }
        return m
    }

    private fun ruotaDiUnQuarto(): Modulo {
        val w = larghezza
        val d = profondita
        // Orario: la casella (x, z) finisce in (d - 1 - z, x).
        val nuovaX = { _: Int, z: Int -> d - 1 - z }
        val nuovaZ = { x: Int, _: Int -> x }

        val righe = MutableList(w) { CharArray(d) { '0' } }
        for (z in 0 until d) for (x in 0 until w) {
            if (caselle[z][x] == '1') righe[nuovaZ(x, z)][nuovaX(x, z)] = '1'
        }

        return copy(
            ingombro = Ingombro(w = d, d = w),
            caselle = righe.map { String(it) },
            connettori = connettori.map {
                it.a(nuovaX(it.x, it.z), nuovaZ(it.x, it.z)).copy(lato = it.lato.ruotato(1))
            },
            partenza = partenza?.let {
                Partenza(listOf(nuovaX(it.x, it.z), nuovaZ(it.x, it.z)), it.verso.ruotato(1))
            },
            arredi = arredi.map { Arredo(it.tipo, listOf(nuovaX(it.x, it.z), nuovaZ(it.x, it.z))) },
            // La pianta vive nello spazio continuo: il punto (px, pz)
            // finisce in (profondita - pz, px), quindi larghezza e
            // profondita' di ogni forma si scambiano.
            pianta = pianta.map {
                it.copy(x = d - it.z - it.d, z = it.x, w = it.d, d = it.w)
            }
        )
    }
}
