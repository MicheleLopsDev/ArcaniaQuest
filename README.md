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

Prova prima i pezzi della **famiglia opposta** a quella da cui arriva:
e' la regola che evita cinque corridoi di fila e sale attaccate l'una
all'altra senza niente in mezzo. Gli attacchi rimasti liberi alla fine
vengono murati, cosi' il sotterraneo non si affaccia mai sul nulla.

Dove due pezzi si incastrano nasce un **passaggio**. Se almeno uno dei
due connettori ha il battente, li' c'e' una porta: parte chiusa, si apre
con un tasto, e **una volta aperta resta aperta** — il gruppo non torna
sui propri passi per ritrovarsi la strada sbarrata.

Il catalogo sta in [`content/moduli/catalogo.json`](content/moduli/catalogo.json),
il formato è spiegato in [`doc/MODULI.md`](doc/MODULI.md).

## 3. La scelta tecnica

**Kotlin + libGDX.** Kotlin 2.4.10, libGDX 1.14.2, Gradle 8.14.4, AGP
8.13.2, bytecode Java 17. Android da **API 26** (Android 8) in su,
compilato contro l'API 36.

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

Per ora **non c'e' nessun modello importato**: la geometria si genera dal
JSON. Quando servira' portare dentro pezzi modellati a mano — arredi,
mostri — si usera' **glTF**, che e' aperto e lo legge qualunque
programma di modellazione.

### Le texture

Quattro sole: pietra da muro, pietrisco da pavimento, legno, ferro. Le
originali stanno in [`res/`](res); quelle che usa il gioco sono in
[`content/texture/`](content/texture), ridotte a 1024x512 e in JPEG —
772 KB in tutto invece di 4,5 MB, che su un telefono si sentono.

Si ripetono ogni **due caselle in orizzontale e una in verticale**,
perche' le immagini sono larghe il doppio di quanto sono alte e cosi' le
pietre restano nelle giuste proporzioni invece di schiacciarsi. Il
pavimento si specchia invece di ripetersi: i bordi combaciano quasi, e
la specchiatura toglie di mezzo la cucitura senza chiedere niente a chi
le ha disegnate.

Se un file manca, il gioco parte lo stesso a tinta unita. Non e' un
vezzo: un asset che non si carica deve degradare, non piantare.

### I moduli del progetto

```
:regole     Kotlin puro. Catalogo, moduli, rotazioni, validatore.
            Zero dipendenze grafiche, zero Android: si prova con JUnit.
:gioco      libGDX. Le mesh generate dai moduli, la telecamera a caselle,
            le luci, l'input.
:desktop    Lanciatore LWJGL3 — Windows e Linux.
:android    Lanciatore Android.
strumenti/  Script per il catalogo: validatore e tavola di correzione.
            Non e' ancora un modulo Gradle.
```

### In Android Studio

Il progetto si apre come un normale progetto Gradle: sincronizza e sei a
posto. Due cose da sapere, perche' Android Studio si comporta diversamente
da IntelliJ su un progetto misto come questo.

**Metti la vista su «Project».** In alto a sinistra, nel menu a tendina
sopra l'albero dei file, la vista predefinita e' «Android»: quella nasconde
i moduli che non sono Android, quindi `:regole`, `:gioco` e `:desktop`
sembrano non esserci. Con «Project» si vedono tutti e quattro.

**Le configurazioni di avvio sono gia' nel repo**, in `.run/`. Android
Studio non ne genera da solo per un modulo JVM in un progetto Android: le
trovi belle e pronte nel menu a tendina in alto, accanto al tasto Play.

| Configurazione | Cosa fa |
|---|---|
| `Desktop` | Apre il gioco sul modulo S25 |
| `Desktop - altro modulo` | Lo stesso, ma sul C45 — cambia `--args` per provarne altri |
| `Prove regole` | I test di `:regole`, senza aprire niente |
| `APK debug` | Costruisce l'apk |

Se dopo un sync non le vedi, chiudi e riapri il progetto: Android Studio
rilegge `.run/` all'apertura.

### Come si prova

```
./gradlew :regole:test          le regole, senza aprire niente
./gradlew perlustra             cammina i sotterranei e verifica che si tengano
./gradlew :desktop:run          il gioco su desktop
./gradlew :android:assembleDebug  l'apk
```

### La perlustrazione

`perlustra` non apre niente: monta i sotterranei, li **cammina un passo
alla volta** come farebbe il gruppo, e controlla di essere arrivato a
ogni casella. Le porte chiuse non fermano — si contano e si passa: una
porta e' un ritardo, non un muro. Fallisce se anche uno solo si
interrompe, quindi puo' stare in una verifica automatica.

```
./gradlew perlustra
./gradlew perlustra -Psemi=1-500 -Ppezzi=16
./gradlew perlustra -Psemi=8BD -Pdiario=si
```

I semi si scrivono in base 36 come dappertutto, quindi `1-200` sono 2592
sotterranei e non duecento. Quando uno si interrompe, il suo diario
finisce in `build/perlustrazioni/`: c'e' ogni singolo passo, e in fondo
le caselle mai raggiunte e la mappa a caratteri. `-Pdiario=si` scrive il
diario anche di quelli riusciti.

Su desktop `:desktop:run` accetta l'id di un modulo e un'opzione per
fotografarlo invece di aprirlo:

```
./gradlew :desktop:run --args="C45"
./gradlew :desktop:run --args="--seme=CRIPTA --pezzi=16"
./gradlew :desktop:run --args="S25 --scatto=vista.png"
./gradlew :desktop:run --args="S25 --alto --scatto=pianta.png"
./gradlew :desktop:run --args="S25 --posa=1,2,ovest --scatto=porta.png"
./gradlew :desktop:run --args="--finestra=460x900"
```

`--cerca=S34,S36` non apre niente: gira i semi finche' non ne trova uno
che contiene tutti i moduli chiesti, e li stampa con l'elenco dei pezzi.
Serve a ritrovare la partita in cui c'e' quello che si vuole guardare.

`--finestra=460x900` prova la disposizione da telefono senza tirare
fuori il telefono. `--mappa`, `--tuttoscoperto` e `--porteaperte` sono
scorciatoie per fotografare stati che a piedi ci vorrebbe un quarto
d'ora a raggiungere.

`--alto` guarda il modulo a picco, senza buio: e' il modo per vedere se
alla mesh manca un pezzo, cosa che da dentro non si nota mai. `--posa`
mette il gruppo in una casella e un verso precisi, cosi' due scatti
fatti a giorni di distanza si possono confrontare.

Lo scatto sta solo qui e non fra le configurazioni di Android Studio: le
virgolette attorno a due argomenti separati da uno spazio non
sopravvivono al passaggio dall'IDE a Gradle.

Lo scatto disegna dodici fotogrammi, salva un PNG e chiude. Serve a
controllare la resa senza doverla guardare: se una modifica rompe la
geometria, si vede in un'immagine invece che in una sessione di gioco.

Comandi: `↑ ↓` avanti e indietro, `← →` volta di 90°, `A D` passo
laterale, `SPAZIO` apre la porta davanti, `M` la mappa a tutto schermo,
`R` monta un sotterraneo nuovo, `F1` nasconde l'interfaccia.

### L'interfaccia

La disposizione viene dal bozzetto di Michele: la vista in prima persona
grande in alto a sinistra, zaino e mappa in colonna a destra, e sotto la
vista la striscia con gruppo, diario e comandi. In verticale — sul
telefono — i pannelli si reimpilano da soli: vista sopra, comandi
grandi sotto.

**Gruppo, zaino e diario sono finti**, e stanno tutti in
`gioco/…/Finti.kt`: quando arrivera' la roba vera si cancella quel file
e il compilatore dice subito chi lo usava. Portano l'etichetta «finto»
nella loro cornice, cosi' non si scambiano per fatti.

**La mappa si scopre camminando.** Non solo le caselle calpestate: anche
quelle che il gruppo *vede*, cioe' quelle a fianco e il corridoio davanti
finche' qualcosa non lo ferma — se no una sala grande resterebbe nera
fino all'ultimo angolo. Le due cose restano distinte: le calpestate
fanno la percentuale di esplorato, le viste fanno il disegno. Stanze e
corridoi hanno tinte diverse anche sulla mappa, perche' nel gioco valgono
regole diverse. `M` la apre a tutto schermo.

Due cose del bozzetto le ho cambiate apposta:

- **I comandi sono relativi, non assoluti.** Il bozzetto aveva quattro
  tasti bussola (N/E/S/O) — e con le frecce scambiate, `[E]` a sinistra
  e `[W]` a destra. Ma in un gioco che gira di 90 gradi alla volta il
  giocatore pensa «avanti, gira, passo di lato», non «vai a est». La
  crociera e' a sei tasti relativi, e la bussola resta come **spia**
  sopra i tasti: dice dove si guarda, non e' un comando.
- **La stanza, la casella e il seme stanno nella barra della vista**,
  che e' quella che si guarda sempre. Il seme li' e' quello che permette
  di raccontare un guaio invece di inseguirlo.

### Il seme

Si scrive **dentro il gioco**: `INVIO` apre il riquadro, si batte il seme,
`INVIO` conferma e il sotterraneo si rimonta. `ESC` annulla. Mentre si
scrive il gruppo non si muove: le lettere sono lettere, non comandi.

Ogni partita nasce da un **seme**, e lo stesso seme rifa' lo stesso
sotterraneo casella per casella. Serve a rigiocare una partita andata
bene, a farsi raccontare da qualcuno dov'e' finito, e soprattutto a
**riprodurre un guaio invece di inseguirlo**.

Si scrive in base 36, cosi' sta in poche lettere: `K7X2M`. Vale anche
una parola qualunque — `--seme=CRIPTA` e' un seme valido, ed e' un modo
comodo per battezzare una partita.

Il seme e' scritto nel titolo della finestra e nel pannello di servizio,
che mostra anche la casella in cui sei, la posizione in metri, il verso,
il modulo, la sua famiglia e quanto sotterraneo hai gia' calpestato.
Quel pannello non e' l'interfaccia del gioco — quella arrivera'
disegnata: e' lo strumento di chi il gioco lo sta costruendo.

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
doc/mock/         I prototipi che hanno guidato le decisioni, e la
                  tavola per correggere il catalogo. Si aprono nel browser.
strumenti/        Validatore del catalogo e generatore della tavola.
regole/ gioco/ desktop/ android/   I quattro moduli Gradle.
LICENSE           GPL-3.0.
```

## 6. A che punto siamo

Fatto:

- La resa e i comandi sono decisi, e sono stati decisi **vedendoli**: in
  `doc/mock/` ci sono la pianta, l'isometrico, il 3D prospettico e il
  prototipo giocabile in prima persona del modulo 25.
- Il formato dei moduli è definito e validato.
- Il catalogo e' completo come numeri: **42 moduli**, 6 iniziali e tutti
  e 36 i tiri del d66.
- Il progetto compila e gira su desktop, e l'apk si costruisce.
- **Il sotterraneo si genera**: i pezzi si pescano col seme, si ruotano,
  si incastrano, e quello che resta libero viene murato. Le porte si
  aprono e restano aperte.
- `:regole` ha le sue prove: catalogo, dadi, rotazioni, generatore.
  Fra queste, che lo stesso seme rifa' lo stesso sotterraneo e che da
  ogni casella si arriva a tutte le altre.

Da fare, nell'ordine:

1. **Verificare le trascrizioni.** Tutti i moduli sono a
   `verificato: false`: vengono da fotografie delle tavole e alcune
   letture sono incerte. Si correggono con la tavola in
   `doc/mock/tavola-moduli.html`.
2. **Il generatore** in `:regole`: pesca, ruota, incastra, rifiuta. La
   rotazione c'e' gia' ed e' provata; manca il piazzamento.
3. **Il gruppo vero**: personaggi, scheda, zaino che contiene qualcosa.
   La cornice c'e' gia' e aspetta solo i dati.
4. Gli incontri, i mostri, i tiri di dado nel diario.
5. Le regole di famiglia che per ora sono solo dato: mostri vaganti nei
   corridoi, incontri fissi e tesoro nelle stanze, gruppo in fila.

Quello che si vede adesso e' volumi grezzi senza texture, luce di torcia
e nebbia. La resa e' quella decisa nei mock, ma non e' ancora vestita.
