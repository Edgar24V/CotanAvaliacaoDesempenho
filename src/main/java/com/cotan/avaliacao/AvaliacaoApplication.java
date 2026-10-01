package com.cotan.avaliacao;

import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AvaliacaoApplication {
    public static void main(String[] args) {
        // Bootstrap reservado para a inicialização Spring do sistema.
        // A entrada JavaFX será adicionada na camada de apresentação.
        org.springframework.boot.SpringApplication.run(AvaliacaoApplication.class, args);
    }
}
