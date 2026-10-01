package com.cotan.avaliacao.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 * Regras de cálculo extraídas e normalizadas do mapa Excel.
 * A lógica permanece independente da UI e do banco de dados.
 */
public class AvaliacaoCalculoService {

    public BigDecimal mediaTres(BigDecimal a, BigDecimal b, BigDecimal c) {
        return media(Arrays.asList(a, b, c), 1);
    }

    public BigDecimal media(Collection<BigDecimal> valores, int casas) {
        if (valores == null || valores.isEmpty()) return null;

        var validos = valores.stream()
                .filter(Objects::nonNull)
                .toList();

        if (validos.isEmpty()) return null;

        BigDecimal soma = validos.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return soma.divide(BigDecimal.valueOf(validos.size()), casas, RoundingMode.HALF_UP);
    }

    public BigDecimal soma(Collection<BigDecimal> valores, int casas) {
        if (valores == null) return null;

        BigDecimal soma = valores.stream()
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return soma.setScale(casas, RoundingMode.HALF_UP);
    }

    public String classificar(BigDecimal nota) {
        if (nota == null) return "";
        if (nota.compareTo(BigDecimal.TEN) < 0) return "Mau";
        if (nota.compareTo(BigDecimal.valueOf(14)) < 0) return "Suficiente";
        if (nota.compareTo(BigDecimal.valueOf(18)) < 0) return "Bom";
        if (nota.compareTo(BigDecimal.valueOf(21)) < 0) return "Muito bom";
        return "";
    }
}
