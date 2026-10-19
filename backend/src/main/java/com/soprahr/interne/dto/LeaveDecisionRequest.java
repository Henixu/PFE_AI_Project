package com.soprahr.interne.dto;

import jakarta.validation.constraints.Size;

/** Commentaire de décision : facultatif pour approuver, obligatoire pour refuser (contrôlé par le service). */
public record LeaveDecisionRequest(@Size(max = 500) String comment) {
}
