package com.cotan.avaliacao.domain;

public enum Trimestre {
    PRIMEIRO(1),
    SEGUNDO(2),
    TERCEIRO(3);

    private final int numero;

    Trimestre(int numero) {
        this.numero = numero;
    }

    public int getNumero() {
        return numero;
    }
}
