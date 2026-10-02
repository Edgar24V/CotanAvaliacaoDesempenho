package com.cotan.avaliacao.domain;

import java.time.LocalDate;

/**
 * Entidade da ficha anual persistida no SQLite.
 */
public record AvaliacaoDesempenhoAnual(
        long id,
        long staffId,
        String academicYear,
        LocalDate evaluationDate,
        LocalDate periodStart,
        LocalDate periodEnd,
        Long evaluatorStaffId,
        String quantitative1,
        String quantitative2,
        String quantitative3,
        String qualitative1,
        String qualitative2,
        String qualitative3,
        String finalQuantitative,
        String finalQualitative,
        String comment1,
        String comment2,
        String comment3,
        String appreciationGeneral,
        String concordance,
        Long homologanteStaffId
) {
    public AvaliacaoDesempenhoAnual {
        academicYear = clean(academicYear);
        quantitative1 = clean(quantitative1);
        quantitative2 = clean(quantitative2);
        quantitative3 = clean(quantitative3);
        qualitative1 = clean(qualitative1);
        qualitative2 = clean(qualitative2);
        qualitative3 = clean(qualitative3);
        finalQuantitative = clean(finalQuantitative);
        finalQualitative = clean(finalQualitative);
        comment1 = clean(comment1);
        comment2 = clean(comment2);
        comment3 = clean(comment3);
        appreciationGeneral = clean(appreciationGeneral);
        concordance = clean(concordance);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
