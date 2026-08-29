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
 * Un varco senza battente nasce gia' aperto e non si richiude: e' un
 * buco nel muro. Una porta nasce chiusa, e una volta aperta **resta
 * aperta** — il gruppo non torna sui propri passi per trovarsi la strada
 * sbarrata di nuovo.
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

    val porteInTutto: Int get() = passaggi.count { it.conBattente }
    val porteAperte: Int get() = passaggi.count { it.conBattente && it.aperta }

    /**
     * Il sotterraneo scritto a caratteri, per guardarlo senza aprire una
     * finestra: un punto dove si cammina, un piu' dove c'e' una porta
     * chiusa, un apostrofo dove il passaggio e' aperto, una chiocciola
     * sulla partenza. Serve nei log e nelle prove.
     */
    fun disegno(): String {
        val celle = pezzi.flatMap { it.celleMondo() }
        if (celle.isEmpty()) return "(vuoto)"
        val x0 = celle.minOf { it.x }; val x1 = celle.maxOf { it.x }
        val z0 = celle.minOf { it.z }; val z1 = celle.maxOf { it.z }
        val sb = StringBuilder()
        sb.append("seme ").append(Sorte.scrivi(seme))
            .append("  pezzi ").append(pezzi.size)
            .append("  caselle ").append(caselleInTutto)
            .append("  porte ").append(porteInTutto)
            .append("  origine ").append(x0).append(",").append(z0)
            .append(RIGA)
        for (z in z0..z1) {
            for (x in x0..x1) {
                sb.append(
                    when {
                        partenza.x == x && partenza.z == z -> CHIOCCIOLA
                        !calpestabile(x, z) -> VUOTO
                        passaggi.any { it.conBattente && !it.aperta && it.collega(x, z) } -> CHIUSA
                        passaggi.any { it.collega(x, z) } -> APERTA
                        else -> SUOLO
                    }
                )
            }
            sb.append(RIGA)
        }
        for (p in pezzi) {
            val aperti = varchiDi(p.chiave).sorted()
            sb.append(p.chiave.padEnd(8)).append(" a ").append(p.ox).append(",").append(p.oz)
                .append("   connettori ")
            for ((i, k) in p.modulo.connettori.withIndex()) {
                sb.append(if (i in aperti) "aperto" else "murato")
                    .append("(").append(k.lato.name.lowercase()).append(" ")
                    .append(k.x).append(",").append(k.z).append(") ")
            }
            sb.append(RIGA)
        }
        for (p in passaggi.filter { it.conBattente }) {
            sb.append(if (p.aperta) "aperta " else "chiusa ")
                .append(p.a.x).append(",").append(p.a.z).append("  -  ")
                .append(p.b.x).append(",").append(p.b.z).append(RIGA)
        }
        return sb.toString()
    }

    companion object {
        // i segni della mappa a caratteri
        private const val RIGA = "\n"
        private const val VUOTO = ' '
        private const val SUOLO = '.'
        private const val CHIUSA = '+'
        private const val APERTA = '"'
        private const val CHIOCCIOLA = '@'

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
