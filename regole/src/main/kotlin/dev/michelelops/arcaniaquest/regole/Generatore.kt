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

    /**
     * I passaggi aperti finora: le porte da una parte, e dall'altra quali
     * connettori di quale pezzo sono ormai diventati un buco nel muro.
     */
    private class Cantiere {
        val passaggi = mutableListOf<Porta>()
        val varchi = HashMap<String, MutableSet<Int>>()

        fun eGiaAperto(a: Aperto) = a.indice in (varchi[a.chiave] ?: emptySet())

        /**
         * Unisce due connettori che si guardano in faccia.
         *
         * Il varco si segna da tutte e due le parti, se no da un lato
         * resta il muro e dall'altro il buco. Il battente invece lo
         * disegna il solo pezzo [la]: quale dei due sia e' arbitrario, ma
         * dev'essere uno solo o se ne vedono due sovrapposti.
         */
        fun apriVarco(qua: Aperto, la: Aperto, conBattente: Boolean) {
            varchi.getOrPut(qua.chiave) { mutableSetOf() } += qua.indice
            varchi.getOrPut(la.chiave) { mutableSetOf() } += la.indice
            passaggi += Porta(
                a = qua.cella,
                b = la.cella,
                conBattente = conBattente,
                proprietario = if (conBattente) la.chiave to la.indice else null
            )
        }
    }

    fun genera(sorte: Sorte, quantiPezzi: Int = 12): Dungeon {
        require(quantiPezzi >= 1) { "un dungeon ha almeno un pezzo" }

        val iniziale = catalogo.iniziale(sorte.d6())
        val primo = Piazzato("${iniziale.id}#0", iniziale, 0, 0)

        val posati = mutableListOf(primo)
        val occupate = HashSet<Cella>(primo.celleMondo())
        val cantiere = Cantiere()

        val frontiera = ArrayDeque<Aperto>()
        frontiera += apertiDi(primo)
        // gli attacchi scartati dal ciclo, che la cucitura riprende in mano
        val avanzati = mutableListOf<Aperto>()

        var contatore = 1
        while (frontiera.isNotEmpty() && posati.size < quantiPezzi) {
            val attacco = frontiera.removeFirst()
            val vicina = Cella(attacco.cella.x + attacco.lato.dx, attacco.cella.z + attacco.lato.dz)
            // di la' c'e' gia' qualcosa: non ci si mette niente, ma
            // l'attacco resta in lista perche' la cucitura potrebbe
            // ancora unirlo a un connettore che lo guarda in faccia
            if (vicina in occupate) { avanzati += attacco; continue }

            val messo = provaAPiazzare(attacco, vicina, occupate, sorte, contatore) ?: continue
            contatore++

            posati += messo.pezzo
            occupate += messo.pezzo.celleMondo()

            val entrata = Aperto(messo.pezzo.chiave, messo.indice, vicina, attacco.lato.opposto)
            cantiere.apriVarco(
                qua = attacco,
                la = entrata,
                conBattente = messo.pezzo.modulo.connettori[messo.indice].porta ||
                    connettoreDi(posati, attacco).porta
            )

            frontiera += apertiDi(messo.pezzo).filter { it.indice != messo.indice }
        }

        cuci(posati, frontiera.toList() + avanzati, cantiere)

        val partenza = iniziale.partenza
        return Dungeon(
            seme = sorte.seme,
            pezzi = posati,
            partenza = partenza?.let { Cella(it.x, it.z) } ?: primo.celleMondo().first(),
            versoIniziale = partenza?.verso ?: Lato.NORD,
            passaggi = cantiere.passaggi,
            varchi = cantiere.varchi
        )
    }

    /**
     * Cuce gli attacchi rimasti liberi che si guardano in faccia.
     *
     * Capita spesso che due pezzi finiscano affiancati per vie diverse,
     * con un connettore per uno che da' sulla casella dell'altro. Senza
     * questa passata resterebbero due muri schiena contro schiena, e da
     * dentro il gioco sembra un corridoio che finisce contro una stanza
     * senza motivo. Cucirli apre il passaggio, e in piu' crea gli anelli:
     * un sotterraneo tutto ad albero costringe a rifare sempre la stessa
     * strada a ritroso.
     */
    private fun cuci(posati: List<Piazzato>, liberi: List<Aperto>, cantiere: Cantiere) {
        val perPezzo = posati.associateBy { it.chiave }
        // solo quelli che non sono gia' diventati un varco
        val aperti = liberi.filterNot { cantiere.eGiaAperto(it) }
        val perCasella = aperti.groupBy { it.cella }

        for (a in aperti) {
            if (cantiere.eGiaAperto(a)) continue
            val vicina = Cella(a.cella.x + a.lato.dx, a.cella.z + a.lato.dz)
            val dirimpetto = perCasella[vicina]?.firstOrNull {
                it.chiave != a.chiave && it.lato == a.lato.opposto && !cantiere.eGiaAperto(it)
            } ?: continue

            fun conPorta(x: Aperto) = perPezzo.getValue(x.chiave).modulo.connettori[x.indice].porta
            cantiere.apriVarco(qua = a, la = dirimpetto, conBattente = conPorta(a) || conPorta(dirimpetto))
        }
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
