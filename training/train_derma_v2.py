#!/usr/bin/env python3
"""
train_derma_v2.py
-----------------
Seconda versione del modello per la segmentazione di lesioni cutanee.
Rispetto alla v1: risoluzione 512x512, dataset combinato, gradient accumulation
e resume automatico da checkpoint.

Dataset      : ISIC 2018 Task 1 + IMA++ (majority vote + singolo annotatore)
Architettura : U-Net + MobileNetV2 (pretrained ImageNet)
Loss         : BCE + Dice (modulo nn.Module)
Risoluzione  : 512x512
Epoche       : 40
"""

import os
import glob
import random

import numpy as np
import torch
import torch.nn as nn
import pandas as pd
from torch.utils.data import Dataset, DataLoader
from PIL import Image
import segmentation_models_pytorch as smp
from tqdm import tqdm
import albumentations as A
from albumentations.pytorch import ToTensorV2

device = "cuda" if torch.cuda.is_available() else "cpu"

# Dataset ISIC 2018
isic18_image_dir = "/archive/ISIC2018_Task1-2_Training_Input"
isic18_mask_dir  = "/archive/ISIC2018_Task1_Training_GroundTruth"

# Dataset IMA++
imapp_image_dir    = "/derma-ai/dataset_v2/images"
imapp_mask_dir     = "/derma-ai/dataset_v2/segs"
imapp_metadata_csv = "/derma-ai/dataset_v2/seg_metadata.csv"

# Output
best_model_path  = "/derma-ai/derma_seg_v2.pth"       # pesi migliori
checkpoint_path  = "/derma-ai/derma_seg_v2_ckpt.pth"  # checkpoint per resume
onnx_output_path = "/derma-ai/derma_seg_v2.onnx"

# Iperparametri
image_size         = 512
batch_size         = 4
accumulation_steps = 4    # gradient accumulation: simula batch effettivo da 16
num_epochs         = 40
learning_rate      = 3e-4
val_split_ratio    = 0.15  # 15% dei dati usato per la validazione
random_seed        = 42

# Fissa i seed per riproducibilità degli esperimenti
random.seed(random_seed)
np.random.seed(random_seed)
torch.manual_seed(random_seed)


# Training: crop casuale, flip, rotazioni, colori, rumore, deformazione elastica
train_augmentation = A.Compose([
    A.RandomResizedCrop(
        size=(image_size, image_size),
        scale=(0.3, 1.0),
        ratio=(0.75, 1.33),
        p=1.0
    ),
    A.HorizontalFlip(p=0.5),
    A.VerticalFlip(p=0.5),
    A.Rotate(limit=45, p=0.5),
    A.ColorJitter(brightness=0.3, contrast=0.3, saturation=0.2, hue=0.1, p=0.5),
    A.GaussNoise(p=0.3),
    # Deformazione elastica per simulare variazioni morfologiche delle lesioni
    A.ElasticTransform(alpha=120, sigma=120 * 0.05, p=0.3),
    A.Normalize(mean=(0.485, 0.456, 0.406), std=(0.229, 0.224, 0.225)),
    ToTensorV2(),
])

# Validation: solo resize e normalizzazione, nessuna augmentation casuale
val_augmentation = A.Compose([
    A.Resize(image_size, image_size),
    A.Normalize(mean=(0.485, 0.456, 0.406), std=(0.229, 0.224, 0.225)),
    ToTensorV2(),
])


class SkinLesionDataset(Dataset):
    """
    Dataset generico per la segmentazione di lesioni cutanee.
    Accetta una lista di coppie (percorso_immagine, percorso_maschera).
    """

    def __init__(self, image_mask_pairs, augmentation=None):
        self.image_mask_pairs = image_mask_pairs
        self.augmentation     = augmentation

    def __len__(self):
        return len(self.image_mask_pairs)

    def __getitem__(self, index):
        image_path, mask_path = self.image_mask_pairs[index]

        image = np.array(Image.open(image_path).convert("RGB"))
        mask  = np.array(Image.open(mask_path).convert("L"))

        # Binarizza la maschera: pixel > 127 → lesione, resto → sfondo
        mask = (mask > 127).astype(np.float32)

        if self.augmentation:
            result = self.augmentation(image=image, mask=mask)
            image  = result["image"]
            mask   = result["mask"].unsqueeze(0)  # aggiunge dimensione canale

        return image, mask


def load_isic18_pairs():
    """Carica coppie (immagine, maschera) dal dataset ISIC 2018."""
    image_paths = sorted(glob.glob(os.path.join(isic18_image_dir, "*.jpg")))
    pairs = []

    for image_path in image_paths:
        stem = os.path.basename(image_path).replace(".jpg", "")
        # Le maschere ISIC hanno il suffisso "_segmentation.png"
        mask_path = os.path.join(isic18_mask_dir, stem + "_segmentation.png")
        if os.path.exists(mask_path):
            pairs.append((image_path, mask_path))

    print(f"ISIC 2018: {len(pairs)} coppie trovate")
    return pairs


def load_imapp_pairs():
    """
    Carica coppie (immagine, maschera) dal dataset IMA++.
    Priorità maschera: majority vote (MV) > singolo annotatore (ST) > primo disponibile.
    """
    metadata      = pd.read_csv(imapp_metadata_csv)
    grouped_by_id = metadata.groupby("ISIC_id")
    pairs         = []
    skipped       = 0

    for isic_id, annotation_group in grouped_by_id:
        image_path = os.path.join(imapp_image_dir, f"{isic_id}.jpg")

        if not os.path.exists(image_path):
            skipped += 1
            continue

        # Seleziona la maschera in ordine di affidabilità
        majority_vote_rows    = annotation_group[annotation_group["annotator"] == "MV"]
        single_annotator_rows = annotation_group[annotation_group["annotator"] == "ST"]

        if len(majority_vote_rows) > 0:
            segmentation_filename = majority_vote_rows.iloc[0]["seg_filename"]
        elif len(single_annotator_rows) > 0:
            segmentation_filename = single_annotator_rows.iloc[0]["seg_filename"]
        else:
            segmentation_filename = annotation_group.iloc[0]["seg_filename"]

        mask_path = os.path.join(imapp_mask_dir, segmentation_filename)
        if os.path.exists(mask_path):
            pairs.append((image_path, mask_path))

    print(f"IMA++: {len(pairs)} coppie trovate ({skipped} skip)")
    return pairs


def build_datasets():
    """
    Unisce ISIC 2018 e IMA++, mescola casualmente e divide in train/val
    secondo val_split_ratio.
    """
    isic18_pairs = load_isic18_pairs()
    imapp_pairs  = load_imapp_pairs()
    all_pairs    = isic18_pairs + imapp_pairs

    random.shuffle(all_pairs)

    split_index = int(len(all_pairs) * (1 - val_split_ratio))
    train_pairs = all_pairs[:split_index]
    val_pairs   = all_pairs[split_index:]

    print(f"Totale: {len(all_pairs)} | Train: {len(train_pairs)} | Val: {len(val_pairs)}")

    return (
        SkinLesionDataset(train_pairs, train_augmentation),
        SkinLesionDataset(val_pairs,   val_augmentation),
    )


class DiceBCELoss(nn.Module):
    """
    Combina BCE e Dice loss.
    - BCE: penalizza ogni pixel individualmente
    - Dice: massimizza l'overlap spaziale (robusto a classi sbilanciate)
    """

    def __init__(self):
        super().__init__()
        self.bce = nn.BCEWithLogitsLoss()

    def forward(self, logits, targets):
        bce_term     = self.bce(logits, targets)
        sigmoid_prob = torch.sigmoid(logits)
        intersection = (sigmoid_prob * targets).sum(dim=(2, 3))

        # Smooth=1 per evitare divisione per zero
        dice_term = 1 - (2 * intersection + 1) / (
            sigmoid_prob.sum(dim=(2, 3)) + targets.sum(dim=(2, 3)) + 1
        )

        return bce_term + dice_term.mean()


def build_model():
    """U-Net con encoder MobileNetV2 pre-addestrato su ImageNet."""
    return smp.Unet(
        encoder_name    = "mobilenet_v2",
        encoder_weights = "imagenet",
        in_channels     = 3,
        classes         = 1,
    ).to(device)


def train():
    train_dataset, val_dataset = build_datasets()

    train_loader = DataLoader(
        train_dataset, batch_size=batch_size,
        shuffle=True, num_workers=4, pin_memory=True
    )
    val_loader = DataLoader(
        val_dataset, batch_size=batch_size,
        shuffle=False, num_workers=4, pin_memory=True
    )

    model     = build_model()
    criterion = DiceBCELoss()
    optimizer = torch.optim.Adam(model.parameters(), lr=learning_rate)
    # LR decresce seguendo una curva coseno nel corso delle epoche
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=num_epochs)

    start_epoch   = 1
    best_val_loss = float("inf")

    # Riprendi da checkpoint se presente
    if os.path.exists(checkpoint_path):
        print(f"Resuming da checkpoint: {checkpoint_path}")
        checkpoint = torch.load(checkpoint_path, map_location=device)
        model.load_state_dict(checkpoint["model"])
        optimizer.load_state_dict(checkpoint["optimizer"])
        scheduler.load_state_dict(checkpoint["scheduler"])
        start_epoch   = checkpoint["epoch"] + 1
        best_val_loss = checkpoint["best_val"]
        print(f"  → Riparto da epoca {start_epoch}, best_val={best_val_loss:.4f}")

    for epoch in range(start_epoch, num_epochs + 1):

        # Training con gradient accumulation
        model.train()
        running_train_loss = 0.0
        optimizer.zero_grad()

        for step, (images, masks) in enumerate(tqdm(train_loader, desc=f"Ep {epoch}/{num_epochs} train")):
            images = images.to(device, dtype=torch.float32)
            masks  = masks.to(device, dtype=torch.float32)

            # Divide la loss per accumulation_steps prima del backward
            loss = criterion(model(images), masks) / accumulation_steps
            loss.backward()
            running_train_loss += loss.item() * accumulation_steps

            # Aggiorna i pesi ogni accumulation_steps batch
            if (step + 1) % accumulation_steps == 0:
                optimizer.step()
                optimizer.zero_grad()

        # Flush dell'ultimo step parziale (se il totale non è multiplo di accumulation_steps)
        optimizer.step()
        optimizer.zero_grad()

        # Validazione
        model.eval()
        running_val_loss = 0.0

        with torch.no_grad():
            for images, masks in tqdm(val_loader, desc=f"Ep {epoch}/{num_epochs} val  "):
                images = images.to(device, dtype=torch.float32)
                masks  = masks.to(device, dtype=torch.float32)
                running_val_loss += criterion(model(images), masks).item()

        avg_train_loss = running_train_loss / len(train_loader)
        avg_val_loss   = running_val_loss   / len(val_loader)
        scheduler.step()

        print(f"Epoch {epoch:02d} | train={avg_train_loss:.4f} | val={avg_val_loss:.4f}")

        # Salva checkpoint ad ogni epoca per permettere il resume
        torch.save({
            "epoch":     epoch,
            "model":     model.state_dict(),
            "optimizer": optimizer.state_dict(),
            "scheduler": scheduler.state_dict(),
            "best_val":  best_val_loss,
        }, checkpoint_path)
        print(f"  → Checkpoint salvato (ep {epoch})")

        # Salva i pesi migliori se la val loss migliora
        if avg_val_loss < best_val_loss:
            best_val_loss = avg_val_loss
            torch.save(model.state_dict(), best_model_path)
            print(f"  ✓ Best salvato (val={best_val_loss:.4f})")

    print(f"\nDone. Best val loss: {best_val_loss:.4f}")
    return model


def export_onnx(model):
    """
    Esporta il modello migliore in formato ONNX per l'inferenza su Android
    tramite ONNX Runtime Mobile.
    """
    model.load_state_dict(torch.load(best_model_path, map_location=device))
    model.eval()

    # Input fittizio per tracciare il grafo del modello
    dummy_input = torch.randn(1, 3, image_size, image_size).to(device)

    torch.onnx.export(
        model, dummy_input, onnx_output_path,
        opset_version = 18,
        input_names   = ["input"],
        output_names  = ["output"],
        # Asse batch dinamico: permette inferenza con batch size variabile
        dynamic_axes  = {"input": {0: "batch"}, "output": {0: "batch"}},
    )
    print(f"ONNX esportato: {onnx_output_path}")

    # Il file .data viene generato automaticamente per modelli grandi (>2GB)
    if os.path.exists(onnx_output_path + ".data"):
        print(f"  → Copia anche: {onnx_output_path}.data")


if __name__ == "__main__":
    print(f"Device: {device}")
    trained_model = train()
    export_onnx(trained_model)
