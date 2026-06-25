"""
train_derma_v1.py
-----------------
Prima versione del modello per la segmentazione di lesioni cutanee.

Dataset      : ISIC 2018 Task 1 (segmentazione lesioni dermatologiche)
Architettura : U-Net + MobileNetV2 (pretrained ImageNet)
Loss         : BCE + Dice (implementazione manuale)
Risoluzione  : 256x256
Output finale: modello esportato in formato ONNX
"""

import os

import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader
from torchvision import transforms
from PIL import Image
from tqdm import tqdm
import segmentation_models_pytorch as smp

# Percorsi dataset
train_image_dir = "/archive/ISIC2018_Task1-2_Training_Input"
train_mask_dir  = "/archive/ISIC2018_Task1_Training_GroundTruth"
val_image_dir   = "/archive/ISIC2018_Task1-2_Validation_Input"

# Iperparametri
image_size       = 256
batch_size       = 8
num_epochs       = 40
learning_rate    = 1e-4
device           = torch.device("cuda" if torch.cuda.is_available() else "cpu")
checkpoint_path  = "/derma-ai/best_model.pth"
onnx_output_path = "/derma-ai/derma_seg.onnx"


class ISICDataset(Dataset):
    """
    Dataset per ISIC 2018 Task 1.
    Supporta sia la modalità training (con maschere) che inference (solo immagini).
    """

    def __init__(self, image_dir, mask_dir=None, size=256):
        self.image_dir  = image_dir
        self.mask_dir   = mask_dir
        self.size       = size

        # Raccoglie tutti gli ID campione (nome file senza estensione)
        self.sample_ids = sorted([
            filename[:-4]
            for filename in os.listdir(image_dir)
            if filename.lower().endswith(".jpg")
        ])

        # Resize, tensor e normalizzazione ImageNet
        self.image_transform = transforms.Compose([
            transforms.Resize((size, size)),
            transforms.ToTensor(),
            transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225]),
        ])

        # Interpolazione NEAREST per non alterare i bordi della maschera
        self.mask_transform = transforms.Compose([
            transforms.Resize((size, size), interpolation=Image.NEAREST),
            transforms.ToTensor(),
        ])

    def __len__(self):
        return len(self.sample_ids)

    def __getitem__(self, index):
        sample_id = self.sample_ids[index]

        image = Image.open(os.path.join(self.image_dir, sample_id + ".jpg")).convert("RGB")
        image = self.image_transform(image)

        if self.mask_dir:
            # Le maschere ISIC hanno il suffisso "_segmentation.png"
            mask = Image.open(
                os.path.join(self.mask_dir, sample_id + "_segmentation.png")
            ).convert("L")
            mask = self.mask_transform(mask)
            # Binarizza: pixel > 0.5 → lesione, resto → sfondo
            mask = (mask > 0.5).float()
            return image, mask

        return image


def dice_bce_loss(predictions, targets):
    """
    Combina BCE e Dice loss.
    - BCE penalizza ogni pixel singolarmente
    - Dice massimizza l'overlap tra predizione e target (utile con classi sbilanciate)
    """
    bce = nn.functional.binary_cross_entropy_with_logits(
        predictions.float(), targets.float()
    )

    sigmoid_prob = torch.sigmoid(predictions.float())
    intersection = (sigmoid_prob * targets).sum(dim=(2, 3))
    union        = sigmoid_prob.sum(dim=(2, 3)) + targets.sum(dim=(2, 3))

    # Smooth=1 per evitare divisione per zero
    dice = 1 - (2 * intersection + 1) / (union + 1)

    return bce + dice.mean()


def main():
    print(f"Device: {device}")

    train_dataset = ISICDataset(train_image_dir, train_mask_dir, image_size)
    val_dataset   = ISICDataset(val_image_dir, None, image_size)
    train_loader  = DataLoader(
        train_dataset, batch_size=batch_size,
        shuffle=True, num_workers=2, pin_memory=True
    )
    print(f"Train: {len(train_dataset)} imgs | Val: {len(val_dataset)} imgs")

    # U-Net con encoder MobileNetV2 pre-addestrato su ImageNet
    model = smp.Unet(
        encoder_name="mobilenet_v2",
        encoder_weights="imagenet",
        in_channels=3,
        classes=1,
    ).to(device)

    optimizer = torch.optim.Adam(model.parameters(), lr=learning_rate)
    # Riduce il LR se la loss non migliora per 3 epoche consecutive
    scheduler = torch.optim.lr_scheduler.ReduceLROnPlateau(optimizer, patience=3, factor=0.5)

    best_val_loss = float("inf")

    for epoch in range(1, num_epochs + 1):
        model.train()
        running_loss = 0.0

        for images, masks in tqdm(train_loader, desc=f"Epoch {epoch}/{num_epochs}"):
            images, masks = images.to(device), masks.to(device)

            optimizer.zero_grad()
            predictions = model(images)
            loss = dice_bce_loss(predictions, masks)
            loss.backward()

            # Gradient clipping per stabilità del training
            torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
            optimizer.step()

            running_loss += loss.item()

        avg_loss = running_loss / len(train_loader)
        scheduler.step(avg_loss)
        print(f"  Loss: {avg_loss:.4f}  LR: {optimizer.param_groups[0]['lr']:.6f}")

        # Salva il checkpoint solo se la loss migliora
        if avg_loss < best_val_loss:
            best_val_loss = avg_loss
            torch.save(model.state_dict(), checkpoint_path)
            print(f"  ✓ Checkpoint salvato (loss={best_val_loss:.4f})")

    # Export ONNX
    print("\nExport ONNX...")
    model.load_state_dict(torch.load(checkpoint_path, map_location=device))
    model.eval()

    # Input fittizio per tracciare il grafo del modello
    dummy_input = torch.randn(1, 3, image_size, image_size).to(device)

    torch.onnx.export(
        model, dummy_input, onnx_output_path,
        input_names=["input"],
        output_names=["output"],
        # Asse batch dinamico: permette inferenza con batch size variabile
        dynamic_axes={"input": {0: "batch"}, "output": {0: "batch"}},
        opset_version=17,
    )
    print(f"✓ Modello esportato: {onnx_output_path}")


if __name__ == "__main__":
    main()
