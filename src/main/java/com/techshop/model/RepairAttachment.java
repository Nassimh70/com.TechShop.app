package com.techshop.model;

import java.time.LocalDateTime;

/**
 * Pièce jointe d'un dossier de réparation (table repair_attachments) : photo de
 * l'appareil, devis scanné... Le fichier est copié dans le dossier de
 * l'application (data/repairs/&lt;numéro&gt;/).
 */
public record RepairAttachment(int id, int repairId, String fileName, String filePath,
                               String mimeType, Long sizeBytes, LocalDateTime uploadedAt,
                               Integer userId) {
}
