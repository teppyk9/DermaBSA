[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

<img src="app/src/main/res/img/SigilloAteneoTestoColori.svg" style="float: right; width: 250px;" alt="Insubria Logo">

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

1. **Welcome** — schermata iniziale; pulsante di accesso alla lista pazienti
2. **Lista pazienti** — elenco pazienti registrati, creazione nuovo paziente
3. **Home** — riepilogo BSA totale e aree PASI della sessione corrente
4. **Dettaglio paziente** — storico sessioni e misure per distretto
5. **Mappa corporea** — selezione del distretto su vista frontale o posteriore
6. **Fotocamera** — acquisizione tramite CameraX o selezione dalla galleria
7. **Ritaglio** — posizionamento della silhouette del distretto sull'immagine con pan, zoom e rotazione
8. **Selezione lesioni** — segmentazione automatica via ONNX o selezione manuale a pennello
9. **Risultato** — overlay della maschera, valore BSA del distretto, salvataggio nella sessione


## Modello di segmentazione corporea (`body_seg.onnx`) — accantonato

Il modello è stato addestrato su COCO val2017 (U-Net + MobileNetV2, 256×256) con
l'obiettivo di isolare il corpo umano dallo sfondo prima di passare l'immagine al
modello lesioni. I file `body_seg.onnx` e `body_seg.onnx.data` sono presenti negli
asset ma **non vengono più utilizzati**: la pipeline di pre-processing aggiuntiva
introduceva artefatti che aumentavano la percentuale d'errore sulla segmentazione
delle lesioni rispetto all'inferenza diretta su foto originale.

---

## Training — Modello di segmentazione lesioni (`derma_seg_v2.onnx`)

### Versione 1 — `derma_seg.onnx` (deprecata)

Primo modello addestrato su sola ISIC 2018 Task 1 a risoluzione 256×256.
Presentava scarsa generalizzazione su foto cliniche reali scattate a distanza
normale, perché ISIC 2018 contiene immagini dermoscopiche molto ravvicinate.

| Parametro | Valore |
|-----------|--------|
| Dataset | [ISIC 2018 Task 1](https://challenge.isic-archive.com/data/#2018) (2.594 coppie immagine/maschera) |
| Risoluzione | 256×256 |
| Batch size | 8 |
| Epoche | 40 |
| Ottimizzatore | Adam + CosineAnnealingLR |
| Loss | Dice + BCE (float32 esplicito) |
| Training loss finale | **0.059** |
| Tempo training | ~3 ore (2.12 it/s) |

**Problema risolto durante il training:** la loss diventava NaN con `autocast`
(mixed precision). Rimosso `GradScaler` e `autocast`, passando a float32
esplicito su tutto il forward pass.

### Versione 2 — `derma_seg_v2.onnx` (corrente)

Per migliorare la generalizzazione su foto a distanza normale, il dataset è
stato ampliato con IMA++ e la risoluzione portata a 512×512.

#### Dataset

| Dataset | Coppie immagine/maschera | Note |
|---------|--------------------------|------|
| [ISIC 2018 Task 1](https://challenge.isic-archive.com/data/#2018) | 2.594 | Immagini dermoscopiche ravvicinate |
| [IMA++](https://zenodo.org/records/14201693) (Zenodo, doi: 10.5281/zenodo.14201692) | 14.967 | Foto cliniche a distanza variabile, annotazioni consensus MV + singolo annotatore |
| **Totale** | **17.561** | Split 85% train / 15% val |

#### Architettura e configurazione

| Parametro | Valore |
|-----------|--------|
| Architettura | U-Net |
| Encoder | MobileNetV2 (ImageNet pretrained) |
| Risoluzione input | 512×512 |
| Batch size | 4 (gradient accumulation 4 step → batch effettivo 16) |
| Epoche | 40 |
| Ottimizzatore | Adam, lr=3e-4 |
| Loss | Dice + BCE (float32) |
| Framework | PyTorch + `segmentation_models_pytorch` |
| Python | 3.13.5, venv in `/home/teppa/derma-ai/venv` |

#### Augmentation

`RandomResizedCrop(scale 0.3–1.0)`, `HorizontalFlip`, `VerticalFlip`,
`Rotate(±45°)`, `ColorJitter`, `GaussNoise`, `ElasticTransform`,
normalizzazione ImageNet. Libreria: albumentations 2.0.8 (API con `size=(H,W)`
invece di `height=`/`width=` separati rispetto alle versioni precedenti).

#### Checkpoint resume

A metà training è stato aggiunto il salvataggio dello stato completo
(`model.state_dict` + `optimizer.state_dict` + numero epoca) per poter
riprendere in caso di interruzione. Il training è ripreso dall'epoca 15 dopo
l'aggiunta della funzionalità.

#### Risultati

| Metrica | Valore |
|---------|--------|
| Best val loss | **0.2662** |
| Best epoch | 36 / 40 |
| Tempo training totale | ~14 ore |
| Hardware | GTX 1650 4GB, CUDA |

Il best checkpoint intermedio all'epoca 14 aveva val loss 0.2743; il training
completato ha migliorato ulteriormente il risultato fino a 0.2662 all'epoca 36.

#### Export ONNX

| File | Dimensione |
|------|------------|
| `derma_seg_v2.onnx` | 359 KB |
| `derma_seg_v2.onnx.data` | 26 MB |
 
---

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
| `derma_bsa.ui.fragment` | i nove Fragment |
| `derma_bsa.ui.view` | `CropOverlayView`, `SelectionCanvasView` |
| `derma_bsa.ui.adapter` | `PatientAdapter`, `PatientDetailAdapter` |

## Test

`BsaLogicTest` copre la logica pura di `BsaCalculator`, eseguibile senza
dispositivo (`./gradlew test`):

| Test | Cosa verifica |
|------|---------------|
| `mappaSomma100` | somma BSA dei distretti = 100 per tutte e quattro le fasce d'età |
| `sommaMultiDistretto` | calcolo BSA totale su più distretti |
| `bandeAreaScorePasi` | conversione percentuale → area score (0–6) secondo le bande PASI |
| `percentualePasiTronco` | percentuale PASI del tronco per tutte le fasce d'età |
