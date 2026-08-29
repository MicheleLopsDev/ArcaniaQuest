# ArcaniaQuest

Un dungeon crawler in prima persona, a caselle, dove **il sotterraneo è
diverso a ogni partita**: non è disegnato a mano, è montato pescando pezzi
da un catalogo e incastrandoli uno dopo l'altro.

Gira su **Windows, Linux e Android** da un solo codice, scritto in Kotlin.
È software libero (GPL-3.0), e le regole si appoggiano al materiale D&D
rilasciato in licenza aperta.

---

## 1. Che gioco è

Si esplora in prima persona, come in *Eye of the Beholder* o *Dungeon
Master*: si vede il corridoio davanti, si avanza **una casella alla
volta**, si gira **di 90 gradi**. Non esiste telecamera libera e non
esiste movimento continuo.

Non è nostalgia. È la scelta da cui discende tutto il resto:

- la posizione del gruppo è una casella più un verso fra quattro — quattro
  numeri, non una matrice;
- la collisione è una domanda sola: *la casella davanti è calpestabile?*
  Nessun motore fisico;
- i mostri si muovono di casella in casella, quindi niente inseguimento
  continuo da calcolare;
- a schermo c'è il modulo corrente e poco altro: qualche centinaio di
  poligoni, non un sotterraneo intero. È il motivo per cui il gioco può
  girare bene su un telefono senza rinunciare al 3D.

La resa è quella dei mock in `doc/mock/`: pietra grezza, luce di torcia
che si muove, buio che si mangia il fondo delle stanze.

## 2. Il dungeon a moduli

Il sotterraneo si costruisce con dei **pezzi** presi da un catalogo
trascritto dalle tavole del gioco da tavolo. I pezzi sono di tre famiglie,
e la famiglia non è una questione di forma: **cambia le regole**.

| Famiglia | Pescato con | Cosa fa |
|---|---|---|
| `iniziale` | d6 | Il pezzo da cui parte la partita, con la scala d'ingresso. Se ne pesca uno solo. |
| `corridoio` | d66 | Collega. Mostri solo vaganti, gruppo in fila, agguati possibili, niente tesoro né riposo. |
| `stanza` | d66 | Ospita. Incontri fissi, formazione libera, tesori, arredi, riposo se è vuota. |

Il generatore lavora **solo sui connettori**, mai sulla forma: prende un
attacco libero, pesca un pezzo che abbia un attacco sul lato opposto, lo
ruota, lo trasla, e rifiuta il piazzamento se qualcosa si sovrappone. Che
una sala sia ovale o quadrata non lo riguarda.

Il catalogo sta in [`content/moduli/catalogo.json`](content/moduli/catalogo.json),
il formato è spiegato in [`doc/MODULI.md`](doc/MODULI.md).

## 3. La scelta tecnica

**Kotlin + libGDX**, JDK 21, Gradle 8.11.

Le tre piattaforme sono un requisito, non un desiderio, e sono la ragione
principale della scelta:

- **Windows e Linux** escono dallo stesso backend (LWJGL3) e dallo stesso
  jar. Non c'è un ramo di codice per sistema operativo.
- **Android** ha un backend ufficiale e maturo nella stessa libreria: la
  stessa scena, lo stesso ciclo di gioco, cambia solo il lanciatore.
- La geometria di un crawler a caselle è modesta. Un motore grosso
  (jMonkeyEngine, Godot) porterebbe editor, scenegrafi e sistemi che qui
  non servono, e su Android costerebbe il doppio in peso e in grattacapi.
- libGDX è una libreria, non un ambiente: si chiama da Kotlin come
  qualunque altra dipendenza, e soprattutto **si può lasciare fuori** dal
  modulo che contiene le regole.

Per i modelli si usa **glTF** (`gdx-gltf`), formato aperto e leggibile da
qualunque strumento di modellazione.

### I moduli del progetto

```
:regole     Kotlin puro. Catalogo, generatore, stato della partita, regole.
            Zero dipendenze grafiche, zero Android: si prova con JUnit.
:gioco      libGDX. Le mesh generate dai moduli, la telecamera a caselle,
            l'interfaccia, l'input.
:desktop    Lanciatore LWJGL3 — Windows e Linux.
:android    Lanciatore Android.
:strumenti  Riga di comando ed editor per il catalogo dei moduli.
```

La regola che tiene in piedi la separazione: **`:regole` non sa che esiste
uno schermo.** Se una decisione di gioco ha bisogno di sapere quanti
poligoni ci sono, la decisione è nel posto sbagliato.

### Vincoli che non si negoziano

- `:regole` senza dipendenze da libGDX né da Android.
- Il dato è la fonte: la mesh si **genera** dai moduli, non si disegna a
  mano. Se una stanza cambia forma, cambia il JSON.
- Niente fisica. La collisione è la griglia.
- Nessuna telecamera libera, nessun movimento continuo.
- Si salvano i fatti, i bonus si ricalcolano.
- ID canonici nei dati, nomi visibili solo nei file di localizzazione.

## 4. Il materiale

Il gioco è **nostro**, ispirato a D&D e ad altri giochi, non una copia di
nessuno. Il materiale arriva da due parti:

- **Le risorse D&D aperte.** Il System Reference Document 5.1 è
  disponibile sotto **Creative Commons Attribution 4.0**: si può usare,
  modificare e ridistribuire anche dentro un progetto GPL, a patto di
  citare la fonte come chiede la licenza. Quando entrerà materiale SRD nel
  progetto, l'attribuzione va in un file `NOTICE` in radice — e va
  verificata sul testo ufficiale, non ricopiata a memoria.
  Attenzione: nell'SRD **non c'è tutto**. Diverse creature e nomi famosi
  ne restano fuori. Quello che manca lo inventiamo, non lo prendiamo.
- **Il materiale di Michele.** Tavole, regole, ambientazione e contenuti
  originali. È lui che li fornisce; il ruolo del codice è metterli in
  gioco senza tradirli.

Il codice è GPL-3.0 (vedi [`LICENSE`](LICENSE)). Le licenze del materiale
di gioco sono un'altra cosa e vanno tenute distinte: quando arriveranno
asset con licenza propria (font, texture, suoni), ognuno avrà la sua
riga nel `NOTICE`.

## 5. Dove sta cosa

```
content/moduli/   Il catalogo dei pezzi del dungeon.
doc/              Documentazione. MODULI.md e' il formato dei pezzi.
doc/mock/         I prototipi che hanno guidato le decisioni,
                  navigabili nel browser.
LICENSE           GPL-3.0.
```

## 6. A che punto siamo

Fatto:

- La resa e i comandi sono decisi, e sono stati decisi **vedendoli**: in
  `doc/mock/` ci sono la pianta, l'isometrico, il 3D prospettico e il
  prototipo giocabile in prima persona del modulo 25.
- Il formato dei moduli è definito e validato.
- 18 pezzi trascritti sui 42 previsti (6 iniziali su 6, 12 su 36 della
  tavola d66).

Da fare, nell'ordine:

1. Completare il catalogo: mancano i moduli d66 dal 31 al 66, e le 18
   trascrizioni esistenti vanno confrontate con le tavole stampate
   (`verificato: false` su tutte, alcune sono letture incerte).
2. Lo scheletro Gradle coi cinque moduli, e il primo eseguibile che apre
   una finestra su desktop.
3. Il generatore in `:regole`: pesca, ruota, incastra, rifiuta.
4. La mesh generata dal JSON, e il modulo 25 che gira davvero nel motore.
5. Il gruppo, la scheda dei personaggi, gli incontri.
