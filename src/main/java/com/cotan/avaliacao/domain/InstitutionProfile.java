package com.cotan.avaliacao.domain;

/**
 * Configuração institucional única usada pelas fichas e relatórios.
 */
public record InstitutionProfile(
        long id,
        String provincialOffice,
        String municipalDirection,
        String school,
        Long defaultEvaluatorStaffId,
        Long defaultHomologanteStaffId
) {
    public InstitutionProfile {
        provincialOffice = clean(provincialOffice);
        municipalDirection = clean(municipalDirection);
        school = clean(school);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
