# DermaBSA

App Android per la stima della superficie corporea (BSA) affetta da psoriasi
e il calcolo del punteggio PASI. Sviluppata per il corso di Programmazione di
Dispositivi Mobili — Università dell'Insubria.

## Contesto

Il BSA psoriasico indica la percentuale di cute del paziente interessata dalla
malattia. Il PASI (Psoriasis Area and Severity Index) è il punteggio clinico
standard che combina estensione e gravità delle lesioni in quattro macro-aree:
testa, arti superiori, tronco e arti inferiori.

L'app calcola la componente "area" del PASI partendo da foto reali delle
lesioni, usando un modello AI per identificarle automaticamente. Le percentuali
BSA dei distretti si calcolano con la tavola di Lund-Browder modificata, che
tiene conto dell'età del paziente perché le proporzioni corporee cambiano
dall'infanzia all'età adulta.

## Requisiti

- Android 11 (API 30) o superiore
- Architettura arm64-v8a
- File del modello: `derma_seg_v2.onnx` + `derma_seg_v2.onnx.data`
  da mettere in `app/src/main/assets/` prima di compilare

## Come compilare

```
./gradlew assembleDebug      # build APK debug
./gradlew installDebug       # installa su dispositivo connesso
./gradlew test               # esegue i test JVM
```

Su Windows usare `gradlew.bat` al posto di `./gradlew`.

## Funzionamento

L'app gestisce un archivio di pazienti, ognuno associato a una fascia d'età
(0–4, 5–9, 10–14, 15+ anni). Per ogni distretto anatomico il medico:

1. seleziona la regione sulla mappa corporea (fronte o retro)
2. scatta una foto o ne carica una dalla galleria
3. ritaglia l'immagine sulla silhouette del distretto (pan, zoom, rotazione)
4. lascia che il modello AI rilevi le lesioni, oppure le seleziona a mano
   con il pennello
5. salva la misura nella sessione corrente

La schermata principale mostra il BSA totale accumulato, i contributi per
distretto e le percentuali delle quattro aree PASI con il relativo area score (0–6).

## Architettura

Singola Activity con sei Fragment navigati tramite `FragmentManager`:

```
HomeFragment → BodyMapFragment → CameraFragment → CropFragment → SelectionFragment → ResultFragment
```

Lo stato della sessione è condiviso tramite `AppViewModel` e `StateFlow`.

### Database (Room, versione 4)

Tre tabelle: `pazienti`, `sessioni`, `misure`. Il database include le
migrazioni dalla versione 1 alla 4. La migrazione 3→4 rinomina la colonna
`dataNascita` in `etaAnni` perché il campo contiene l'età rappresentativa
della fascia (2, 7, 12 o 20 anni) e non una data di nascita.

### Modello AI

`OnnxHelper` gestisce la sessione ONNX Runtime. Il modello accetta un'immagine
512×512 normalizzata con i valori ImageNet e restituisce una mappa di
probabilità per pixel. La soglia di binarizzazione è calcolata con il metodo
di Otsu invece di usare un valore fisso, con un range calibrato per catturare
anche lesioni a basso contrasto.

Quando l'utente seleziona manualmente un'area con il pennello, il modello
lavora solo sul bounding box di quella selezione (scalato a 512×512), il che
migliora la precisione rispetto all'analisi sull'intera immagine.

### Calcolo BSA/PASI

Le percentuali BSA dipendono dalla fascia d'età tramite `regioniPerEta()`.
Il BSA del distretto si ottiene moltiplicando la percentuale del distretto per
la frazione di pixel classificati come lesione. Il PASI area score si ricava
confrontando il BSA misurato con la superficie totale della macro-area.

## Test

`BsaLogicTest` verifica la logica pura di `BsaCalculator` senza dipendenze
Android: controlla che la somma BSA dei distretti sia 100% per ogni fascia
d'età, il calcolo multi-distretto, la conversione in area score e le
percentuali PASI per tutte e quattro le fasce.

## Dipendenze principali

- ONNX Runtime 1.17.0
- CameraX 1.3.0
- Room 2.6.1
- Lifecycle / ViewModel / StateFlow

## Limitazioni

- ABI limitata ad `arm64-v8a` per compatibilità con le librerie native ONNX
- I file del modello non sono inclusi nel repository per via delle dimensioni
- Il modello analizza un distretto alla volta, non c'è analisi globale del corpo
