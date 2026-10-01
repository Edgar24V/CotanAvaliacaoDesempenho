package com.cotan.avaliacao;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.concurrent.CompletableFuture;

/**
 * Ponto de entrada da aplicação desktop Cotan - Avaliação e Desempenho.
 *
 * A aplicação usa JavaFX como interface gráfica e Spring Boot como camada
 * de serviços/persistência. O Spring é inicializado em segundo plano para
 * que a janela apareça imediatamente e o programa não termine logo após o
 * arranque.
 */
public class AvaliacaoApplication extends Application {

    private ConfigurableApplicationContext springContext;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("Cotan • Avaliação e Desempenho");
        stage.setMinWidth(980);
        stage.setMinHeight(620);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f5f7fb;");

        VBox loading = new VBox(18);
        loading.setAlignment(Pos.CENTER);

        Label title = new Label("COTAN");
        title.setFont(Font.font("System", FontWeight.BOLD, 32));
        title.setTextFill(Color.web("#17365D"));

        Label subtitle = new Label("Sistema de Avaliação e Desempenho");
        subtitle.setFont(Font.font("System", FontWeight.NORMAL, 18));
        subtitle.setTextFill(Color.web("#526173"));

        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(48, 48);

        Label status = new Label("A iniciar o sistema...");
        status.setFont(Font.font("System", 14));
        status.setTextFill(Color.web("#526173"));

        loading.getChildren().addAll(title, subtitle, progress, status);
        root.setCenter(loading);

        Scene scene = new Scene(root, 1100, 700);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();

        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> {
            if (springContext != null) {
                springContext.close();
            }
            Platform.exit();
        });

        // O arranque do Spring/JPA ocorre fora da thread gráfica.
        CompletableFuture.runAsync(() -> {
            try {
                springContext = new SpringApplicationBuilder(AvaliacaoApplication.class)
                        .headless(false)
                        .run();

                Platform.runLater(() -> showDashboard(stage));
            } catch (Exception ex) {
                Platform.runLater(() -> showStartupError(root, ex));
            }
        });
    }

    private void showDashboard(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f5f7fb;");

        VBox header = new VBox(5);
        header.setPadding(new Insets(28, 34, 24, 34));
        header.setStyle("-fx-background-color: #17365D;");

        Label brand = new Label("COTAN");
        brand.setFont(Font.font("System", FontWeight.BOLD, 28));
        brand.setTextFill(Color.WHITE);

        Label title = new Label("Avaliação e Desempenho");
        title.setFont(Font.font("System", FontWeight.NORMAL, 20));
        title.setTextFill(Color.web("#dbe8f7"));

        header.getChildren().addAll(brand, title);

        VBox content = new VBox(18);
        content.setPadding(new Insets(34));
        content.setAlignment(Pos.TOP_LEFT);

        Label welcome = new Label("Painel principal");
        welcome.setFont(Font.font("System", FontWeight.BOLD, 26));
        welcome.setTextFill(Color.web("#17365D"));

        Label description = new Label(
                "O sistema foi iniciado corretamente e está conectado ao banco de dados SQLite.");
        description.setWrapText(true);
        description.setFont(Font.font("System", 15));
        description.setTextFill(Color.web("#526173"));

        VBox databaseCard = new VBox(8);
        databaseCard.setPadding(new Insets(20));
        databaseCard.setStyle(
                "-fx-background-color: white;" +
                "-fx-background-radius: 12;" +
                "-fx-border-color: #dfe5ec;" +
                "-fx-border-radius: 12;");

        Label dbTitle = new Label("Banco de dados");
        dbTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        dbTitle.setTextFill(Color.web("#17365D"));

        Label dbStatus = new Label("● SQLite — conectado");
        dbStatus.setFont(Font.font("System", FontWeight.NORMAL, 14));
        dbStatus.setTextFill(Color.web("#26734d"));

        databaseCard.getChildren().addAll(dbTitle, dbStatus);

        content.getChildren().addAll(welcome, description, databaseCard);

        Label footer = new Label("Cotan Avaliação e Desempenho • Sistema desktop");
        footer.setPadding(new Insets(14, 20, 14, 20));
        footer.setTextFill(Color.web("#718096"));

        root.setTop(header);
        root.setCenter(content);
        root.setBottom(footer);

        stage.setScene(new Scene(root, 1100, 700));
        stage.centerOnScreen();
    }

    private void showStartupError(BorderPane root, Throwable error) {
        String message = error.getMessage() == null
                ? error.getClass().getSimpleName()
                : error.getMessage();

        VBox box = new VBox(14);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(30));

        Label title = new Label("Não foi possível iniciar o sistema");
        title.setFont(Font.font("System", FontWeight.BOLD, 22));
        title.setTextFill(Color.web("#9b2c2c"));

        Label detail = new Label(message);
        detail.setWrapText(true);
        detail.setMaxWidth(700);
        detail.setTextFill(Color.web("#526173"));

        box.getChildren().addAll(title, detail);
        root.setCenter(box);
    }

    @Override
    public void stop() {
        if (springContext != null) {
            springContext.close();
        }
    }
}
