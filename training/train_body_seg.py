"""
train_body_seg.py
-----------------
Addestramento di una U-Net (encoder MobileNetV2) per la segmentazione
binaria del corpo umano su sfondo.

Architettura : U-Net + MobileNetV2 (pretrained ImageNet)
Loss         : BCE + Dice
Ottimizzatore: Adam con ReduceLROnPlateau
Input        : immagini RGB 256x256
Output       : maschera binaria (corpo / sfondo)
"""

import os
import glob

import cv2
import numpy as np
import torch
from torch.utils.data import Dataset, DataLoader
import segmentation_models_pytorch as smp
import albumentations as A
from albumentations.pytorch import ToTensorV2
from tqdm import tqdm

# Percorsi dataset
train_image_dir = "/dataset/train/images"
train_mask_dir  = "/dataset/train/masks"
val_image_dir   = "/dataset/val/images"
val_mask_dir    = "/dataset/val/masks"

# Iperparametri
image_size      = 256
batch_size      = 8
num_epochs      = 30
learning_rate   = 1e-4
device          = "cuda" if torch.cuda.is_available() else "cpu"
checkpoint_path = "/body-seg/best_body_seg.pth"

# Training: flip, variazioni di luminosità/contrasto, shift/scale/rotate
train_transform = A.Compose([
    A.Resize(image_size, image_size),
    A.HorizontalFlip(p=0.5),
    A.RandomBrightnessContrast(p=0.3),
    A.ShiftScaleRotate(shift_limit=0.05, scale_limit=0.1, rotate_limit=15, p=0.4),
    A.Normalize(mean=(0.485, 0.456, 0.406), std=(0.229, 0.224, 0.225)),
    ToTensorV2(),
])

# Validation: solo resize e normalizzazione, nessuna augmentation casuale
val_transform = A.Compose([
    A.Resize(image_size, image_size),
    A.Normalize(mean=(0.485, 0.456, 0.406), std=(0.229, 0.224, 0.225)),
    ToTensorV2(),
])


class BodySegDataset(Dataset):
    """Carica coppie immagine/maschera da disco e applica le augmentation."""

    def __init__(self, image_dir, mask_dir, transform):
        self.image_paths = sorted(glob.glob(f"{image_dir}/*.jpg"))
        self.mask_paths  = sorted(glob.glob(f"{mask_dir}/*.png"))
        self.transform   = transform

    def __len__(self):
        return len(self.image_paths)

    def __getitem__(self, index):
        # Carica immagine in RGB e maschera in scala di grigi
        image = cv2.cvtColor(cv2.imread(self.image_paths[index]), cv2.COLOR_BGR2RGB)
        mask  = cv2.imread(self.mask_paths[index], cv2.IMREAD_GRAYSCALE)

        # Binarizza la maschera: pixel > 127 → 1 (corpo), resto → 0 (sfondo)
        mask = (mask > 127).astype(np.uint8)

        augmented = self.transform(image=image, mask=mask)
        return augmented["image"].float(), augmented["mask"].unsqueeze(0).float()


train_dataset = BodySegDataset(train_image_dir, train_mask_dir, train_transform)
val_dataset   = BodySegDataset(val_image_dir,   val_mask_dir,   val_transform)

train_loader = DataLoader(train_dataset, batch_size=batch_size, shuffle=True,  num_workers=2)
val_loader   = DataLoader(val_dataset,   batch_size=batch_size, shuffle=False, num_workers=2)

# U-Net con encoder MobileNetV2 pre-addestrato su ImageNet
model = smp.Unet(
    encoder_name="mobilenet_v2",
    encoder_weights="imagenet",
    in_channels=3,
    classes=1,
    activation=None,      # il sigmoid è incluso nella BCEWithLogitsLoss
).to(device)

optimizer = torch.optim.Adam(model.parameters(), lr=learning_rate)
# Riduce il LR di metà se la val loss non migliora per 3 epoche consecutive
scheduler = torch.optim.lr_scheduler.ReduceLROnPlateau(optimizer, patience=3, factor=0.5)

bce_loss  = torch.nn.BCEWithLogitsLoss()
dice_loss = smp.losses.DiceLoss(mode="binary")

def combined_loss(predictions, targets):
    """Somma di BCE e Dice loss per bilanciare precisione pixel-wise e overlap."""
    return bce_loss(predictions, targets) + dice_loss(predictions, targets)


best_val_loss = float("inf")
print(f"Device: {device}")
print(f"Train: {len(train_dataset)} immagini | Val: {len(val_dataset)} immagini\n")

for epoch in range(1, num_epochs + 1):

    # Training
    model.train()
    running_train_loss = 0.0

    for images, masks in tqdm(train_loader, desc=f"Epoch {epoch}/{num_epochs} [train]"):
        images, masks = images.to(device), masks.to(device)

        optimizer.zero_grad()
        predictions = model(images)
        loss = combined_loss(predictions.float(), masks.float())
        loss.backward()

        # Gradient clipping per evitare esplosione dei gradienti
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
        optimizer.step()

        running_train_loss += loss.item()

    avg_train_loss = running_train_loss / len(train_loader)

    # Validazione
    model.eval()
    running_val_loss = 0.0

    with torch.no_grad():
        for images, masks in tqdm(val_loader, desc=f"Epoch {epoch}/{num_epochs} [val]"):
            images, masks = images.to(device), masks.to(device)
            predictions = model(images)
            running_val_loss += combined_loss(predictions.float(), masks.float()).item()

    avg_val_loss = running_val_loss / len(val_loader)

    scheduler.step(avg_val_loss)
    print(f"  → train_loss={avg_train_loss:.4f}  val_loss={avg_val_loss:.4f}")

    # Salva il modello solo se la val loss migliora
    if avg_val_loss < best_val_loss:
        best_val_loss = avg_val_loss
        torch.save(model.state_dict(), checkpoint_path)
        print(f"  ✓ Salvato (best_val_loss={best_val_loss:.4f})")

print(f"\nTraining completato. Best val loss: {best_val_loss:.4f}")
