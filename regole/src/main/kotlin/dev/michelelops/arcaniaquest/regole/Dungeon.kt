package dev.michelelops.arcaniaquest.regole

/** Perche' non si passa. */
enum class Ostacolo { NIENTE, ROCCIA, MURO, PORTA_CHIUSA }

/**
 * Un modulo messo al suo posto: quale pezzo, gia' ruotato, e da che
 * casella comincia. La rotazione e' dentro [modulo] e non a fianco,
 * perche' un pezzo ruotato a meta' e' un pezzo rotto.
 */
data class Piazzato(
    val chiave: String,
    val modulo: Modulo,
    val ox: Int,
    val oz: Int
) {
    fun calpestabile(x: Int, z: Int): Boolean = modulo.calpestabile(x - ox, z - oz)

    fun celleMondo(): List<Cella> = modulo.celle().map { Cella(it.x + ox, it.z + oz) }

    /** La casella di mondo su cui si affaccia il connettore [i]. */
    fun cellaDi(i: Int): Cella {
        val k = modulo.connettori[i]
        return Cella(k.x + ox, k.z + oz)
    }
}

/**
 * Il passaggio fra due caselle di moduli diversi.
 *
 * Un varco senza battente nasce gia' aperto e non si chiude: e' un buco
 * nel muro, non c'e' niente da tirare. Una porta invece nasce chiusa, e
 * quello che il gruppo le fa **resta fatto** — aperta rimane aperta
 * finche' qualcuno non la richiude, e non si riapre da sola.
 */
class Porta(
    val a: Cella,
    val b: Cella,
    val conBattente: Boolean,
    /** Chi disegna il battente: pezzo e indice del connettore. */
    val proprietario: Pair<String, Int>?
) {
    var aperta: Boolean = !conBattente
        private set

    fun apri(): Boolean {
        if (aperta) return false
        aperta = true
        return true
    }

    /** Un buco nel muro non si chiude: non c'e' niente da tirare. */
    fun chiudi(): Boolean {
        if (!conBattente || !aperta) return false
        aperta = false
        return true
    }

    /** Ritorna true se lo stato e' cambiato davvero. */
    fun commuta(): Boolean = if (aperta) chiudi() else apri()

    fun collega(x: Int, z: Int): Boolean =
        (a.x == x && a.z == z) || (b.x == x && b.z == z)
}

/**
 * Il sotterraneo di una partita: i pezzi al loro posto e i passaggi fra
 * di loro. Tutto in caselle di mondo, che e' l'unico sistema di
 * riferimento che il gioco usa per muoversi.
 */
class Dungeon(
    val seme: Long,
    val pezzi: List<Piazzato>,
    val partenza: Cella,
    val versoIniziale: Lato,
    val passaggi: List<Porta>,
    /** Per ogni pezzo, i connettori che sono davvero un varco. */
    private val varchi: Map<String, Set<Int>>
) {
    private val proprietarioDi: Map<Cella, Piazzato> =
        pezzi.flatMap { p -> p.celleMondo().map { it to p } }.toMap()

    private val perCasella: Map<Cella, List<Porta>> =
        passaggi.flatMap { listOf(it.a to it, it.b to it) }
            .groupBy({ it.first }, { it.second })

    val caselleInTutto: Int get() = proprietarioDi.size

    fun pezzoIn(x: Int, z: Int): Piazzato? = proprietarioDi[Cella(x, z)]

    fun calpestabile(x: Int, z: Int): Boolean = proprietarioDi.containsKey(Cella(x, z))

    fun varchiDi(chiave: String): Set<Int> = varchi[chiave] ?: emptySet()

    /** I connettori di un pezzo che devono ancora mostrare il battente. */
    fun battentiChiusiDi(chiave: String): Set<Int> =
        passaggi.filter { !it.aperta && it.proprietario?.first == chiave }
            .mapNotNull { it.proprietario?.second }
            .toSet()

    fun portaFra(x: Int, z: Int, verso: Lato): Porta? {
        val nx = x + verso.dx
        val nz = z + verso.dz
        return perCasella[Cella(x, z)]?.firstOrNull { it.collega(nx, nz) }
    }

    /**
     * Cosa c'e' fra questa casella e quella accanto.
     *
     * Dentro uno stesso pezzo non ci sono muri interni: se la casella
     * accanto e' calpestabile, ci si passa. Fra pezzi diversi ci si passa
     * solo dove il generatore ha incastrato due connettori.
     */
    fun ostacolo(x: Int, z: Int, verso: Lato): Ostacolo {
        val nx = x + verso.dx
        val nz = z + verso.dz
        val qui = pezzoIn(x, z) ?: return Ostacolo.ROCCIA
        val la = pezzoIn(nx, nz) ?: return Ostacolo.ROCCIA
        if (qui.chiave == la.chiave) return Ostacolo.NIENTE
        val porta = portaFra(x, z, verso) ?: return Ostacolo.MURO
        return if (porta.aperta) Ostacolo.NIENTE else Ostacolo.PORTA_CHIUSA
    }

    /** Apre la porta davanti, se ce n'e' una da aprire. */
    fun apri(x: Int, z: Int, verso: Lato): Porta? {
        val porta = portaFra(x, z, verso) ?: return null
        return if (porta.apri()) porta else null
    }

    /**
     * Apre o chiude la porta davanti, a seconda di com'e' adesso.
     * Ritorna la porta solo se qualcosa e' cambiato: su un varco senza
     * battente non succede niente.
     */
    fun commuta(x: Int, z: Int, verso: Lato): Porta? {
        val porta = portaFra(x, z, verso) ?: return null
        return if (porta.commuta()) porta else null
    }

    val porteInTutto: Int get() = passaggi.count { it.conBattente }

    /**
     * Il sotterraneo scritto a caratteri, per guardarlo senza aprire una
     * finestra: la pianta, poi i connettori pezzo per pezzo, poi le porte.
     * Serve nei log e nelle prove.
     */
    fun disegno(): String = buildString {
        val celle = pezzi.flatMap { it.celleMondo() }
        if (celle.isEmpty()) return "(vuoto)"

        append("seme ").append(Sorte.scrivi(seme))
            .append("  pezzi ").append(pezzi.size)
            .append("  caselle ").append(caselleInTutto)
            .append("  porte ").append(porteInTutto)
            .append("  origine ").append(celle.minOf { it.x }).append(",").append(celle.minOf { it.z })
            .appendLine()

        pianta(celle)
        connettoriDeiPezzi()
        elencoDellePorte()
    }

    /**
     * La pianta: un punto dove si cammina, un piu' dove c'e' una porta
     * chiusa, un apice dove il passaggio e' aperto, una chiocciola sulla
     * partenza, spazio dove c'e' roccia.
     */
    private fun StringBuilder.pianta(celle: List<Cella>) {
        for (z in celle.minOf { it.z }..celle.maxOf { it.z }) {
            for (x in celle.minOf { it.x }..celle.maxOf { it.x }) {
                append(
                    when {
                        partenza.x == x && partenza.z == z -> PARTENZA
                        !calpestabile(x, z) -> PIETRA
                        passaggi.any { it.conBattente && !it.aperta && it.collega(x, z) } -> CHIUSA
                        passaggi.any { it.collega(x, z) } -> APERTA
                        else -> SUOLO
                    }
                )
            }
            appendLine()
        }
    }

    /** Per ogni pezzo, dove sta e quali dei suoi connettori sono diventati un varco. */
    private fun StringBuilder.connettoriDeiPezzi() {
        for (p in pezzi) {
            val aperti = varchiDi(p.chiave)
            append(p.chiave.padEnd(8)).append(" a ").append(p.ox).append(",").append(p.oz)
                .append("   connettori ")
            for ((i, k) in p.modulo.connettori.withIndex()) {
                append(if (i in aperti) "aperto" else "murato")
                    .append("(").append(k.lato.name.lowercase()).append(" ")
                    .append(k.x).append(",").append(k.z).append(") ")
            }
            appendLine()
        }
    }

    private fun StringBuilder.elencoDellePorte() {
        for (p in passaggi.filter { it.conBattente }) {
            append(if (p.aperta) "aperta " else "chiusa ")
                .append(p.a.x).append(",").append(p.a.z).append("  -  ")
                .append(p.b.x).append(",").append(p.b.z).appendLine()
        }
    }

    companion object {
        // i segni della pianta a caratteri
        private const val PIETRA = ' '
        private const val SUOLO = '.'
        private const val CHIUSA = '+'
        private const val APERTA = '"'
        private const val PARTENZA = '@'

        /**
         * Un sotterraneo di un pezzo solo, con tutti i varchi aperti sul
         * nulla. Non e' una partita: serve a guardare un modulo da vicino
         * mentre lo si trascrive dalle tavole.
         */
        fun unoSolo(m: Modulo, seme: Long = 0L): Dungeon {
            val p = Piazzato("${m.id}#0", m, 0, 0)
            val partenza = m.partenza
            return Dungeon(
                seme = seme,
                pezzi = listOf(p),
                partenza = partenza?.let { Cella(it.x, it.z) } ?: p.celleMondo().first(),
                versoIniziale = partenza?.verso ?: Lato.NORD,
                passaggi = emptyList(),
                varchi = mapOf(p.chiave to m.connettori.indices.toSet())
            )
        }
    }
}
