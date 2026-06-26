# DermaBSA

App Android per la stima della percentuale di superficie corporea (BSA) affetta
da psoriasi e il calcolo del punteggio PASI.

**Francesca Rolla — 757922**
**Gianmarco Maffioli — 757587**

---

## Descrizione

Il BSA (Body Surface Area) psoriasico quantifica la percentuale di cute
coinvolta dalla malattia. Il PASI (Psoriasis Area and Severity Index) è lo
score clinico standard: pesa estensione e gravità delle lesioni su quattro
macro-aree (testa 10%, arti superiori 20%, tronco 30%, arti inferiori 40%).

L'app stima la componente "area" del PASI tramite segmentazione automatica
delle lesioni su foto del paziente. Le percentuali BSA dei distretti anatomici
sono calcolate con la tavola di Lund-Browder modificata, che varia in base alla
fascia d'età (0–4, 5–9, 10–14, 15+ anni).

## Requisiti

- minSdk 30 (Android 11), arm64-v8a
- File modello in `app/src/main/assets/`:
  - `derma_seg_v2.onnx`
  - `derma_seg_v2.onnx.data`

## Compilazione

```
./gradlew assembleDebug      # APK debug
./gradlew installDebug       # installa su dispositivo connesso
./gradlew test               # test JVM, nessun dispositivo necessario
./gradlew build              # build completa + test
```

Su Windows: `gradlew.bat`.

## Flusso applicativo

Navigazione lineare, un distretto alla volta:

1. **Home** — riepilogo BSA totale e aree PASI della sessione; lista pazienti
2. **Mappa corporea** — selezione del distretto su vista frontale o posteriore
3. **Fotocamera** — acquisizione tramite CameraX o selezione dalla galleria
4. **Ritaglio** — posizionamento della silhouette del distretto sull'immagine con pan, zoom e rotazione
5. **Selezione lesioni** — segmentazione automatica via ONNX o selezione manuale a pennello
6. **Risultato** — overlay della maschera, valore BSA del distretto, salvataggio nella sessione

## Scelte progettuali

**Inferenza locale (ONNX Runtime).** L'inferenza avviene interamente sul
dispositivo, senza dipendenze di rete. Il modello è in formato ONNX con pesi
esterni (`.onnx.data`), copiato da `assets/` a `filesDir` al primo avvio.

**Soglia adattiva di Otsu.** La binarizzazione della mappa di probabilità usa
il metodo di Otsu invece di una soglia fissa a 0.5. Una soglia fissa produceva
troppi falsi negativi su lesioni a basso contrasto cromatico; Otsu calcola la
soglia ottimale sulla distribuzione di confidenza di ogni singola immagine.

**Crop sul bounding box in modalità manuale.** Quando viene usata la selezione
a pennello, solo il bounding box dell'area dipinta viene scalato a 512×512 e
passato al modello, concentrando tutti i pixel di input sull'area di interesse.

**Età rappresentativa per fascia.** Il database memorizza un intero (2, 7, 12
o 20 anni) che identifica la fascia d'età, non la data di nascita. Il calcolo
BSA richiede solo di sapere a quale riga della tavola Lund-Browder fare
riferimento.

## Architettura

Singola Activity (`MainActivity`) con sei Fragment navigati tramite
`FragmentManager`. Non viene usato il Navigation Component.

Lo stato della sessione è condiviso tra i Fragment tramite `AppViewModel`
(`activityViewModels()`) e `StateFlow`.

**Navigazione:**
```
HomeFragment → BodyMapFragment → CameraFragment → CropFragment → SelectionFragment → ResultFragment
```

**Persistenza — Room v4, tre tabelle:**

| Tabella | Contenuto |
|---------|-----------|
| `pazienti` | anagrafica + fascia d'età |
| `sessioni` | una per visita, collegata al paziente |
| `misure` | una per distretto per sessione, con path foto e maschera |

Il database include le migrazioni dalla versione 1 alla 4.

**Package:**

| Package | Contenuto |
|---------|-----------|
| `derma_bsa` | `MainActivity`, `AppViewModel`, `RegionMeasurement` |
| `derma_bsa.model` | entità Room, DAO, repository, `BodyRegion`, `PasiRegion` |
| `derma_bsa.util` | `OnnxHelper`, `BsaCalculator` |
| `derma_bsa.ui.fragment` | i sei Fragment |
| `derma_bsa.ui.view` | `CropOverlayView`, `SelectionCanvasView`, `BodyMapView` |

## Test

`BsaLogicTest` copre la logica pura di `BsaCalculator`, eseguibile senza
dispositivo (`./gradlew test`):

| Test | Cosa verifica |
|------|---------------|
| `mappaSomma100` | somma BSA dei distretti = 100 per tutte e quattro le fasce d'età |
| `sommaMultiDistretto` | calcolo BSA totale su più distretti |
| `bandeAreaScorePasi` | conversione percentuale → area score (0–6) secondo le bande PASI |
| `percentualePasiTronco` | percentuale PASI del tronco per tutte le fasce d'età |
