package dev.michelelops.arcaniaquest.regole

/**
 * Monta un sotterraneo incastrando i pezzi del catalogo.
 *
 * Lavora **solo sui connettori**, mai sulla forma: prende un attacco
 * libero, pesca un pezzo che ne abbia uno sul lato opposto, lo ruota
 * finche' i due combaciano, lo trasla e rifiuta il piazzamento se una
 * qualunque casella cade su una gia' occupata. Che una sala sia ovale o
 * quadrata non lo riguarda.
 *
 * Tutto quello che sceglie lo sceglie con la [Sorte], quindi lo stesso
 * seme rifa' lo stesso dungeon casella per casella.
 */
class Generatore(private val catalogo: Catalogo) {

    /** Un attacco ancora libero, in coordinate di mondo. */
    private data class Aperto(
        val chiave: String,
        val indice: Int,
        val cella: Cella,
        val lato: Lato
    )

    fun genera(sorte: Sorte, quantiPezzi: Int = 12): Dungeon {
        require(quantiPezzi >= 1) { "un dungeon ha almeno un pezzo" }

        val iniziale = catalogo.iniziale(sorte.d6())
        val primo = Piazzato("${iniziale.id}#0", iniziale, 0, 0)

        val posati = mutableListOf(primo)
        val occupate = HashSet<Cella>(primo.celleMondo())
        val passaggi = mutableListOf<Porta>()
        val varchi = HashMap<String, MutableSet<Int>>()

        val frontiera = ArrayDeque<Aperto>()
        frontiera += apertiDi(primo)

        var contatore = 1
        while (frontiera.isNotEmpty() && posati.size < quantiPezzi) {
            val attacco = frontiera.removeFirst()
            val vicina = Cella(attacco.cella.x + attacco.lato.dx, attacco.cella.z + attacco.lato.dz)
            if (vicina in occupate) continue   // di la' c'e' gia' qualcosa: si tappa

            val messo = provaAPiazzare(attacco, vicina, occupate, sorte, contatore) ?: continue
            contatore++

            posati += messo.pezzo
            occupate += messo.pezzo.celleMondo()

            // il varco si apre da tutte e due le parti, altrimenti da un
            // lato resta un muro e dall'altro un buco
            varchi.getOrPut(attacco.chiave) { mutableSetOf() } += attacco.indice
            varchi.getOrPut(messo.pezzo.chiave) { mutableSetOf() } += messo.indice

            val battente = messo.pezzo.modulo.connettori[messo.indice].porta ||
                connettoreDi(posati, attacco).porta
            passaggi += Porta(
                a = attacco.cella,
                b = vicina,
                conBattente = battente,
                // il battente lo disegna il pezzo nuovo: e' arbitrario,
                // ma dev'essere uno solo o se ne vedono due sovrapposti
                proprietario = if (battente) messo.pezzo.chiave to messo.indice else null
            )

            frontiera += apertiDi(messo.pezzo).filter { it.indice != messo.indice }
        }

        val partenza = iniziale.partenza
        return Dungeon(
            seme = sorte.seme,
            pezzi = posati,
            partenza = partenza?.let { Cella(it.x, it.z) } ?: primo.celleMondo().first(),
            versoIniziale = partenza?.verso ?: Lato.NORD,
            passaggi = passaggi,
            varchi = varchi
        )
    }

    private class Messo(val pezzo: Piazzato, val indice: Int)

    private fun provaAPiazzare(
        attacco: Aperto,
        vicina: Cella,
        occupate: Set<Cella>,
        sorte: Sorte,
        contatore: Int
    ): Messo? {
        for (candidato in candidati(attacco, sorte)) {
            for (quarti in sorte.mescola(listOf(0, 1, 2, 3))) {
                val ruotato = candidato.ruotato(quarti)
                for ((i, k) in ruotato.connettori.withIndex()) {
                    if (k.lato != attacco.lato.opposto) continue
                    val ox = vicina.x - k.x
                    val oz = vicina.z - k.z
                    val pezzo = Piazzato("${ruotato.id}#$contatore", ruotato, ox, oz)
                    if (pezzo.celleMondo().any { it in occupate }) continue
                    return Messo(pezzo, i)
                }
            }
        }
        return null
    }

    /**
     * L'ordine in cui si provano i pezzi.
     *
     * Prima quelli della famiglia opposta a quella da cui si arriva: e'
     * la regola che evita cinque corridoi di fila e sale attaccate l'una
     * all'altra senza niente in mezzo. Se nessuno di quelli entra, si
     * ripiega sugli altri invece di lasciare un vicolo cieco.
     */
    private fun candidati(attacco: Aperto, sorte: Sorte): List<Modulo> {
        val vengoDaCorridoio = attacco.chiave.startsWith("C")
        val (preferiti, ripiego) = catalogo.pescabili.partition {
            if (vengoDaCorridoio) it.famiglia == Famiglia.STANZA else it.famiglia == Famiglia.CORRIDOIO
        }
        return sorte.mescola(preferiti) + sorte.mescola(ripiego)
    }

    private fun apertiDi(p: Piazzato): List<Aperto> =
        p.modulo.connettori.indices.map { i ->
            Aperto(p.chiave, i, p.cellaDi(i), p.modulo.connettori[i].lato)
        }

    private fun connettoreDi(posati: List<Piazzato>, a: Aperto): Connettore =
        posati.first { it.chiave == a.chiave }.modulo.connettori[a.indice]
}
