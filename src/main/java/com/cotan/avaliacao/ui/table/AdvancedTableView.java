package com.cotan.avaliacao.ui.table;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Tabela de dados avançada do Kubata.
 *
 * <p>Dois modos de operação são suportados:</p>
 * <ul>
 *     <li><b>Cliente</b>: compatível com o comportamento tradicional,
 *     usando FilteredList/SortedList sobre uma colecção em memória.</li>
 *     <li><b>Paginado</b>: mantém apenas a página actual na TableView e
 *     carrega os dados de forma assíncrona através de um PageProvider.
 *     Este modo evita colocar dezenas ou centenas de milhares de objectos
 *     JavaFX na memória da tabela.</li>
 * </ul>
 *
 * <p>A API existente é preservada. As novas capacidades são opcionais.</p>
 */
public class AdvancedTableView<S> extends TableView<S> {

    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MIN_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 1000;
    private static final Duration SEARCH_DEBOUNCE = Duration.millis(250);

    private static final ExecutorService PAGE_EXECUTOR =
            Executors.newFixedThreadPool(
                    Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())),
                    runnable -> {
                        Thread thread = new Thread(runnable, "kubata-table-loader");
                        thread.setDaemon(true);
                        return thread;
                    }
            );

    private FilteredList<S> filteredData;
    private final ContextMenu contextMenu = new ContextMenu();
    private final Map<S, Boolean> selectedItems =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Label sortIndicatorLabel = new Label();
    private final Label resultCountLabel = new Label();
    private final Label pageLabel = new Label();
    private final Label loadingLabel = new Label("A carregar...");

    private final Button previousPageButton = new Button();
    private final Button nextPageButton = new Button();
    private final ProgressIndicator loadingIndicator = new ProgressIndicator();

    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private RowDensity rowDensity = RowDensity.COMFORTABLE;

    private Consumer<S> onEditCallback;
    private Consumer<S> onDeleteCallback;
    private Consumer<S> onViewDetailsCallback;
    private Consumer<List<S>> onBatchDeleteCallback;
    private Runnable onRefreshCallback;
    private String entityName = "Item";

    private ObservableList<S> sourceData = FXCollections.observableArrayList();
    private ListChangeListener<S> sourceDataListener;

    private final PauseTransition searchDebounce = new PauseTransition(SEARCH_DEBOUNCE);
    private TextField activeSearchField;
    private ComboBox<Integer> pageSizeCombo;
    private final Label pageSizeLabel = new Label("Por página:");

    private PageProvider<S> pageProvider;
    private Future<?> activeOperation;
    private long pageIndex = 0;
    private int pageSize = DEFAULT_PAGE_SIZE;
    private long totalItems = -1;
    private String remoteFilter = "";
    private String remoteSortColumn = "";
    private SortDirection remoteSortDirection = SortDirection.ASC;
    private final AtomicLong requestSequence = new AtomicLong();

    public AdvancedTableView() {
        super();
        setEditable(true);
        getStyleClass().addAll("bordered", "striped");
        loadingLabel.getStyleClass().add("text-muted");
        setDensity(RowDensity.COMFORTABLE);
        setPlaceholder(new Label("Nenhum registo encontrado."));
        setLoadingIndicator();

        setupContextMenu();
        setupRowFactory();
        setupSortIndicator();

        previousPageButton.setGraphic(IconUtilsForTable.icon("PREVIOUS"));
        nextPageButton.setGraphic(IconUtilsForTable.icon("NEXT"));
        previousPageButton.setAccessibleText("Página anterior");
        nextPageButton.setAccessibleText("Página seguinte");
        previousPageButton.getStyleClass().add("button-outlined");
        nextPageButton.getStyleClass().add("button-outlined");

        previousPageButton.setOnAction(e -> previousPage());
        nextPageButton.setOnAction(e -> nextPage());

        installKeyboardShortcuts();

        getSortOrder().addListener((ListChangeListener<TableColumn<S, ?>>) change -> {
            updateSortIndicator();

            if (pageProvider != null) {
                TableColumn<S, ?> column = getSortOrder().isEmpty()
                        ? null
                        : getSortOrder().get(0);

                remoteSortColumn = column == null ? "" : column.getText();
                remoteSortDirection = column == null || column.getSortType() == TableColumn.SortType.ASCENDING
                        ? SortDirection.ASC
                        : SortDirection.DESC;

                pageIndex = 0;
                refreshPage();
            }
        });
    }

    public AdvancedTableView(ObservableList<S> items) {
        this();
        setData(items);
    }

    private void installKeyboardShortcuts() {
        addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.F) {
                if (activeSearchField != null) {
                    activeSearchField.requestFocus();
                    activeSearchField.selectAll();
                }
                event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.C) {
                copySelectionToClipboard();
                event.consume();
            }
        });
    }

    /**
     * Builder fluente para criação rápida.
     */
    public static <S> Builder<S> builder() {
        return new Builder<>();
    }

    public static class Builder<S> {
        private ObservableList<S> data;
        private String placeholder;
        private SelectionMode selectionMode = SelectionMode.SINGLE;
        private Consumer<S> onEdit;
        private Consumer<S> onDelete;
        private Consumer<S> onViewDetails;
        private Consumer<List<S>> onBatchDelete;
        private Runnable onRefresh;
        private String entityName = "Item";

        public Builder<S> data(ObservableList<S> data) {
            this.data = data;
            return this;
        }

        public Builder<S> placeholder(String placeholder) {
            this.placeholder = placeholder;
            return this;
        }

        public Builder<S> selectionMode(SelectionMode mode) {
            this.selectionMode = mode == null ? SelectionMode.SINGLE : mode;
            return this;
        }

        public Builder<S> onEdit(Consumer<S> callback) {
            this.onEdit = callback;
            return this;
        }

        public Builder<S> onDelete(Consumer<S> callback) {
            this.onDelete = callback;
            return this;
        }

        public Builder<S> onBatchDelete(Consumer<List<S>> callback) {
            this.onBatchDelete = callback;
            return this;
        }

        public Builder<S> onViewDetails(Consumer<S> callback) {
            this.onViewDetails = callback;
            return this;
        }

        public Builder<S> onRefresh(Runnable callback) {
            this.onRefresh = callback;
            return this;
        }

        public Builder<S> entityName(String name) {
            this.entityName = name == null || name.isBlank() ? "Item" : name;
            return this;
        }

        public AdvancedTableView<S> build() {
            AdvancedTableView<S> table = new AdvancedTableView<>();

            if (data != null) {
                table.setData(data);
            }

            if (placeholder != null) {
                table.setPlaceholder(new Label(placeholder));
            }

            table.getSelectionModel().setSelectionMode(selectionMode);
            table.onEditCallback = onEdit;
            table.onDeleteCallback = onDelete;
            table.onBatchDeleteCallback = onBatchDelete;
            table.onViewDetailsCallback = onViewDetails;
            table.onRefreshCallback = onRefresh;
            table.entityName = entityName;
            table.setupContextMenu();

            return table;
        }
    }

    public void setOnEdit(Consumer<S> callback) {
        this.onEditCallback = callback;
        setupContextMenu();
    }

    public void setOnDelete(Consumer<S> callback) {
        this.onDeleteCallback = callback;
        setupContextMenu();
    }

    public void setOnBatchDelete(Consumer<List<S>> callback) {
        this.onBatchDeleteCallback = callback;
        setupContextMenu();
    }

    public void setOnViewDetails(Consumer<S> callback) {
        this.onViewDetailsCallback = callback;
        setupContextMenu();
    }

    public void setOnRefresh(Runnable callback) {
        this.onRefreshCallback = callback;
        setupContextMenu();
    }

    public void setEntityName(String name) {
        this.entityName = name == null || name.isBlank() ? "Item" : name;
        setupContextMenu();
    }

    /**
     * Controla a densidade visual das linhas.
     */
    public void setDensity(RowDensity density) {
        RowDensity next = density == null ? RowDensity.COMFORTABLE : density;
        setFixedCellSize(switch (next) {
            case COMPACT -> 34;
            case COMFORTABLE -> 42;
            case SPACIOUS -> 52;
        });
        rowDensity = next;
    }

    public RowDensity getDensity() {
        return rowDensity;
    }

    /**
     * Modo normal, compatível com as versões anteriores.
     */
    public void setData(ObservableList<S> items) {
        ObservableList<S> safeItems = items == null
                ? FXCollections.observableArrayList()
                : items;

        if (this.sourceDataListener != null) {
            this.sourceData.removeListener(this.sourceDataListener);
        }

        this.sourceData = safeItems;

        this.filteredData = new FilteredList<>(this.sourceData, p -> true);
        SortedList<S> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(this.comparatorProperty());
        setItems(sortedData);

        selectedItems.clear();
        pageProvider = null;
        totalItems = sourceData.size();
        pageIndex = 0;
        updatePageSizeControlVisibility();
        updatePagerState();

        this.sourceDataListener = change -> Platform.runLater(this::updatePagerState);
        this.sourceData.addListener(this.sourceDataListener);
    }

    /**
     * Activa o modo paginado. A TableView mantém somente uma página em memória.
     */
    public void setPageProvider(PageProvider<S> provider) {
        this.pageProvider = Objects.requireNonNull(provider, "PageProvider não pode ser nulo.");
        this.pageIndex = 0;
        this.totalItems = -1;
        this.remoteFilter = "";
        this.remoteSortColumn = "";
        this.remoteSortDirection = SortDirection.ASC;

        cancelActiveOperation();
        setItems(FXCollections.observableArrayList());
        filteredData = null;
        selectedItems.clear();
        updatePageSizeControlVisibility();
        refreshPage();
    }

    public Optional<PageProvider<S>> getPageProvider() {
        return Optional.ofNullable(pageProvider);
    }

    public boolean isPagedMode() {
        return pageProvider != null;
    }

    public long getCurrentPage() {
        return pageIndex;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        int normalized = Math.max(MIN_PAGE_SIZE, Math.min(MAX_PAGE_SIZE, pageSize));
        if (this.pageSize == normalized) {
            return;
        }

        this.pageSize = normalized;
        this.pageIndex = 0;

        if (pageProvider != null) {
            refreshPage();
        } else {
            updatePagerState();
        }
    }

    public long getTotalItems() {
        return totalItems;
    }

    public void setPage(long zeroBasedPage) {
        if (pageProvider == null) {
            return;
        }

        long max = getLastPageIndex();
        pageIndex = Math.max(0, Math.min(zeroBasedPage, max));
        refreshPage();
    }

    public void nextPage() {
        if (pageProvider == null || busy.get()) {
            return;
        }
        if (hasNextPage()) {
            pageIndex++;
            refreshPage();
        }
    }

    public void previousPage() {
        if (pageProvider == null || busy.get()) {
            return;
        }
        if (pageIndex > 0) {
            pageIndex--;
            refreshPage();
        }
    }

    public boolean hasNextPage() {
        if (pageProvider == null) {
            return false;
        }
        if (totalItems < 0) {
            return true;
        }
        return pageIndex + 1 < getPageCount();
    }

    public long getPageCount() {
        if (totalItems <= 0) {
            return totalItems == 0 ? 0 : -1;
        }
        return (totalItems + pageSize - 1) / pageSize;
    }

    /**
     * Carrega a página actual de forma assíncrona.
     */
    public void refreshPage() {
        PageProvider<S> provider = pageProvider;
        if (provider == null) {
            refresh();
            return;
        }

        cancelActiveOperation();
        long requestId = requestSequence.incrementAndGet();

        setLoading(true);
        activeOperation = PAGE_EXECUTOR.submit(() -> {
            try {
                PageResult<S> result = provider.load(
                        new PageRequest(
                                pageIndex,
                                pageSize,
                                remoteFilter,
                                remoteSortColumn,
                                remoteSortDirection
                        )
                );

                if (Thread.currentThread().isInterrupted()) {
                    return;
                }

                Platform.runLater(() -> {
                    if (requestId != requestSequence.get()) {
                        return;
                    }

                    List<S> records = result == null || result.items() == null
                            ? List.of()
                            : result.items();

                    getItems().setAll(records);

                    if (result != null) {
                        totalItems = Math.max(-1, result.totalItems());

                        if (totalItems < 0 && records.size() < pageSize) {
                            totalItems = pageIndex * (long) pageSize + records.size();
                        }
                    }

                    updatePagerState();
                    setLoading(false);
                });
            } catch (CancellationException ignored) {
                // Pedido substituído por outro.
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (requestId != requestSequence.get()) {
                        return;
                    }

                    setLoading(false);
                    setPlaceholder(new Label(
                            "Não foi possível carregar os dados: " + safeMessage(ex)
                    ));
                });
            }
        });
    }

    /**
     * Define o filtro para modo cliente ou modo paginado.
     */
    public void setFilter(Predicate<S> predicate) {
        if (pageProvider != null) {
            return;
        }

        if (filteredData != null) {
            filteredData.setPredicate(predicate == null ? item -> true : predicate);
            totalItems = filteredData.size();
            updatePagerState();
        }
    }

    /**
     * Cancela a operação assíncrona actual.
     */
    public void cancelActiveOperation() {
        Future<?> operation = activeOperation;
        if (operation != null && !operation.isDone()) {
            operation.cancel(true);
        }
        activeOperation = null;
    }

    /**
     * Define um filtro remoto. O PageProvider recebe-o na próxima página.
     */
    public void setRemoteFilter(String filter) {
        remoteFilter = filter == null ? "" : filter.trim();
        if (pageProvider != null) {
            pageIndex = 0;
            refreshPage();
        }
    }

    public String getRemoteFilter() {
        return remoteFilter;
    }

    public BooleanProperty busyProperty() {
        return busy;
    }

    public boolean isBusy() {
        return busy.get();
    }

    public ObservableList<S> getSourceData() {
        return FXCollections.unmodifiableObservableList(sourceData);
    }

    /**
     * Mostra um estado profissional de carregamento sem bloquear a aplicação inteira.
     */
    public void setLoading(boolean loading) {
        busy.set(loading);
        setDisable(false);

        if (loading) {
            loadingIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
            VBox pane = new VBox(8, loadingIndicator, loadingLabel);
            pane.setAlignment(Pos.CENTER);
            setPlaceholder(pane);
        } else {
            setPlaceholder(new Label("Nenhum registo encontrado."));
        }

        updatePagerState();
    }

    /**
     * Barra de pesquisa com debounce. Em modo paginado, envia o texto ao backend.
     */
    public VBox withSearchBar() {
        TextField searchField = new TextField();
        activeSearchField = searchField;
        searchField.setPromptText("Pesquisar...");
        searchField.getStyleClass().add("large");
        searchField.setPrefWidth(300);

        Button clearButton = new Button("", IconUtilsForTable.icon("CLEAR"));
        clearButton.getStyleClass().add("button-icon");
        clearButton.setAccessibleText("Limpar pesquisa");
        clearButton.setOnAction(e -> searchField.clear());

        HBox searchBox = new HBox(6, searchField, clearButton);
        searchBox.setAlignment(Pos.CENTER_LEFT);

        searchDebounce.setOnFinished(event -> {
            String query = searchField.getText() == null ? "" : searchField.getText().trim();

            if (pageProvider != null) {
                setRemoteFilter(query);
                return;
            }

            String normalized = query.toLowerCase(Locale.ROOT);
            setFilter(item -> {
                if (normalized.isBlank()) {
                    return true;
                }

                return getColumns().stream()
                        .anyMatch(column -> {
                            Object cellData = column.getCellData(item);
                            return cellData != null
                                    && cellData.toString()
                                    .toLowerCase(Locale.ROOT)
                                    .contains(normalized);
                        });
            });
        });

        searchField.textProperty().addListener((obs, oldValue, newValue) -> {
            searchDebounce.playFromStart();
        });

        resultCountLabel.getStyleClass().addAll("text-muted", "text-small");
        updatePagerState();

        pageSizeCombo = createPageSizeCombo();
        updatePageSizeControlVisibility();

        HBox toolbar = new HBox(
                10,
                new Label("Pesquisar:"),
                searchBox,
                resultCountLabel,
                pageSizeLabel,
                pageSizeCombo
        );
        toolbar.setPadding(new Insets(7, 8, 7, 8));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchBox, Priority.NEVER);

        return new VBox(toolbar, this);
    }

    /**
     * Barra completa com pesquisa, contadores e paginação.
     */
    public VBox withDataToolbar() {
        VBox wrapper = withSearchBar();

        HBox pager = new HBox(6);
        pager.setAlignment(Pos.CENTER_RIGHT);
        pageLabel.getStyleClass().addAll("text-muted", "text-small");
        pager.getChildren().addAll(
                previousPageButton,
                pageLabel,
                nextPageButton
        );

        ((VBox) wrapper).getChildren().add(pager);
        return wrapper;
    }

    public TableColumn<S, Boolean> createSelectionColumn() {
        CheckBox headerCheck = new CheckBox();
        TableColumn<S, Boolean> column = new TableColumn<>();
        column.setGraphic(headerCheck);
        column.setSortable(false);
        column.setResizable(false);
        column.setPrefWidth(42);

        headerCheck.setOnAction(e -> {
            boolean selected = headerCheck.isSelected();
            getItems().forEach(item -> selectedItems.put(item, selected));
            refresh();
        });

        column.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleBooleanProperty(
                        selectedItems.getOrDefault(cell.getValue(), false)
                )
        );

        column.setCellFactory(tc -> new CheckBoxTableCell<>() {
            @Override
            public void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }

                S rowItem = getTableRow().getItem();
                CheckBox checkBox = new CheckBox();
                checkBox.setSelected(selectedItems.getOrDefault(rowItem, false));
                checkBox.setOnAction(e ->
                        selectedItems.put(rowItem, checkBox.isSelected())
                );
                setGraphic(checkBox);
            }
        });

        return column;
    }

    public List<S> getManuallySelectedItems() {
        synchronized (selectedItems) {
            return selectedItems.entrySet()
                    .stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    private void setupContextMenu() {
        contextMenu.getItems().clear();

        if (onViewDetailsCallback != null) {
            MenuItem viewItem = new MenuItem("Ver detalhes");
            viewItem.setOnAction(e -> {
                S selected = getSelectionModel().getSelectedItem();
                if (selected != null) {
                    onViewDetailsCallback.accept(selected);
                }
            });
            contextMenu.getItems().add(viewItem);
        }

        if (onEditCallback != null) {
            MenuItem editItem = new MenuItem("Editar " + entityName);
            editItem.setOnAction(e -> {
                S selected = getSelectionModel().getSelectedItem();
                if (selected != null) {
                    onEditCallback.accept(selected);
                }
            });
            contextMenu.getItems().add(editItem);
        }

        MenuItem copyItem = new MenuItem("Copiar linha");
        copyItem.setOnAction(e -> copySelectionToClipboard());
        contextMenu.getItems().add(copyItem);

        if (onDeleteCallback != null) {
            contextMenu.getItems().add(new SeparatorMenuItem());

            MenuItem deleteItem = new MenuItem("Eliminar " + entityName);
            deleteItem.setOnAction(e -> {
                S selected = getSelectionModel().getSelectedItem();
                if (selected != null) {
                    onDeleteCallback.accept(selected);
                }
            });
            contextMenu.getItems().add(deleteItem);
        }

        contextMenu.getItems().add(new SeparatorMenuItem());

        Menu exportMenu = new Menu("Exportar");
        MenuItem exportCsvItem = new MenuItem("CSV (dados visíveis)");
        exportCsvItem.setOnAction(e -> exportToCSV(getItems()));
        MenuItem exportAllCsvItem = new MenuItem("CSV (todos os dados)");
        exportAllCsvItem.setOnAction(e -> exportAllToCSV());
        MenuItem exportExcelItem = new MenuItem("Excel (dados visíveis)");
        exportExcelItem.setOnAction(e -> exportToExcel(getItems()));

        exportMenu.getItems().addAll(
                exportCsvItem,
                exportAllCsvItem,
                new SeparatorMenuItem(),
                exportExcelItem
        );
        contextMenu.getItems().add(exportMenu);

        if (onRefreshCallback != null || pageProvider != null) {
            contextMenu.getItems().add(new SeparatorMenuItem());

            MenuItem refreshItem = new MenuItem("Actualizar");
            refreshItem.setOnAction(e -> {
                if (onRefreshCallback != null) {
                    onRefreshCallback.run();
                } else {
                    refreshPage();
                }
            });
            contextMenu.getItems().add(refreshItem);
        }

        Menu columnsMenu = new Menu("Colunas");
        contextMenu.getItems().add(columnsMenu);

        contextMenu.setOnShowing(e -> populateColumnsMenu(columnsMenu));

        MenuItem selectAll = new MenuItem("Seleccionar página actual");
        selectAll.setOnAction(e -> {
            getItems().forEach(item -> selectedItems.put(item, true));
            refresh();
        });

        MenuItem clearSelection = new MenuItem("Limpar selecção");
        clearSelection.setOnAction(e -> {
            selectedItems.clear();
            refresh();
        });

        MenuItem deleteSelected = new MenuItem("Eliminar seleccionados");
        deleteSelected.setOnAction(e -> {
            List<S> selected = getManuallySelectedItems();
            if (selected.isEmpty()) {
                return;
            }

            Alert confirm = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "Eliminar " + selected.size() + " registo(s)?",
                    ButtonType.YES,
                    ButtonType.NO
            );

            confirm.showAndWait().ifPresent(button -> {
                if (button != ButtonType.YES) {
                    return;
                }

                List<S> copy = List.copyOf(selected);

                if (onBatchDeleteCallback != null) {
                    onBatchDeleteCallback.accept(copy);
                } else if (filteredData != null) {
                    filteredData.getSource().removeAll(copy);
                }

                selectedItems.clear();
                refresh();
            });
        });

        MenuItem exportSelected = new MenuItem("Exportar seleccionados para CSV");
        exportSelected.setOnAction(e -> {
            List<S> selected = getManuallySelectedItems();
            if (!selected.isEmpty()) {
                exportToCSV(selected);
            }
        });

        contextMenu.getItems().add(new SeparatorMenuItem());
        contextMenu.getItems().addAll(selectAll, clearSelection);

        Menu densityMenu = new Menu("Densidade");

        for (RowDensity density : RowDensity.values()) {
            RadioMenuItem densityItem = new RadioMenuItem(
                    switch (density) {
                        case COMPACT -> "Compacta";
                        case COMFORTABLE -> "Confortável";
                        case SPACIOUS -> "Espaçosa";
                    }
            );
            densityItem.setSelected(rowDensity == density);
            densityItem.setOnAction(e -> setDensity(density));
            densityMenu.getItems().add(densityItem);
        }

        contextMenu.getItems().add(densityMenu);


        setContextMenu(contextMenu);
    }

    private void updatePageSizeControlVisibility() {
        boolean visible = pageProvider != null;

        if (pageSizeLabel != null) {
            pageSizeLabel.setVisible(visible);
            pageSizeLabel.setManaged(visible);
        }

        if (pageSizeCombo != null) {
            pageSizeCombo.setVisible(visible);
            pageSizeCombo.setManaged(visible);
        }
    }

    private ComboBox<Integer> createPageSizeCombo() {
        ComboBox<Integer> combo = new ComboBox<>(
                FXCollections.observableArrayList(50, 100, 250, 500, 1000)
        );
        combo.setValue(pageSize);
        combo.setPrefWidth(84);
        combo.setAccessibleText("Registos por página");
        combo.setOnAction(e -> setPageSize(combo.getValue()));
        combo.getStyleClass().add("small");
        return combo;
    }

    private void populateColumnsMenu(Menu columnsMenu) {
        columnsMenu.getItems().clear();

        for (TableColumn<S, ?> column : getColumns()) {
            if (column.getText() == null || column.getText().isBlank()) {
                continue;
            }

            CheckMenuItem item = new CheckMenuItem(column.getText());
            item.setSelected(column.isVisible());
            item.setOnAction(e -> {
                column.setVisible(item.isSelected());

                if (!column.isVisible() && countVisibleColumns() == 0) {
                    column.setVisible(true);
                    item.setSelected(true);
                }
            });

            columnsMenu.getItems().add(item);
        }

        MenuItem showAll = new MenuItem("Mostrar todas");
        showAll.setOnAction(e ->
                getColumns().forEach(column -> column.setVisible(true))
        );

        columnsMenu.getItems().add(new SeparatorMenuItem());
        columnsMenu.getItems().add(showAll);
    }

    private int countVisibleColumns() {
        return (int) getColumns().stream()
                .filter(TableColumn::isVisible)
                .count();
    }

    private void setupRowFactory() {
        setRowFactory(table -> {
            TableRow<S> row = new TableRow<>();

            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.SECONDARY && !row.isEmpty()) {
                    getSelectionModel().select(row.getItem());
                    contextMenu.show(row, event.getScreenX(), event.getScreenY());
                    event.consume();
                } else if (event.getButton() == MouseButton.PRIMARY
                        && event.getClickCount() == 2
                        && !row.isEmpty()
                        && onViewDetailsCallback != null) {
                    onViewDetailsCallback.accept(row.getItem());
                }
            });

            return row;
        });
    }

    private void setupSortIndicator() {
        sortIndicatorLabel.getStyleClass().addAll("accent", "text-small");
        updateSortIndicator();
    }

    private void updateSortIndicator() {
        StringBuilder info = new StringBuilder();

        for (TableColumn<S, ?> column : getSortOrder()) {
            info.append(column.getText())
                    .append(column.getSortType() == TableColumn.SortType.ASCENDING ? " ↑" : " ↓")
                    .append("   ");
        }

        sortIndicatorLabel.setText(info.toString().trim());

        if (remoteSortColumn != null && !remoteSortColumn.isBlank()) {
            // Mantém o mesmo indicador visual no modo remoto.
            sortIndicatorLabel.setText(
                    remoteSortColumn
                            + (remoteSortDirection == SortDirection.ASC ? " ↑" : " ↓")
            );
        }
    }

    private void copySelectionToClipboard() {
        S selected = getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        ClipboardContent content = new ClipboardContent();
        content.putString(formatRowAsTsv(selected));
        Clipboard.getSystemClipboard().setContent(content);
    }

    private String formatRowAsTsv(S item) {
        StringJoiner joiner = new StringJoiner("	");

        for (TableColumn<S, ?> column : getVisibleColumns()) {
            Object data = column.getCellData(item);
            joiner.add(data == null ? "" : data.toString());
        }

        return joiner.toString();
    }

    private List<TableColumn<S, ?>> getVisibleColumns() {
        return getColumns().stream()
                .filter(TableColumn::isVisible)
                .toList();
    }

    private void exportAllToCSV() {
        if (pageProvider == null) {
            exportToCSV(sourceData);
            return;
        }

        File file = chooseFile("Exportar todos os dados para CSV", "*.csv", "CSV");
        if (file == null) {
            return;
        }

        cancelActiveOperation();
        long requestId = requestSequence.incrementAndGet();
        setLoading(true);

        activeOperation = PAGE_EXECUTOR.submit(() -> {
            try (BufferedWriter writer = Files.newBufferedWriter(
                    file.toPath(),
                    StandardCharsets.UTF_8
            )) {
                writer.write("\uFEFF");
                writeCsvHeader(writer);

                long exportPage = 0;
                long expectedTotal = totalItems;

                while (!Thread.currentThread().isInterrupted()) {
                    PageResult<S> result = pageProvider.load(new PageRequest(
                            exportPage,
                            pageSize,
                            remoteFilter,
                            remoteSortColumn,
                            remoteSortDirection
                    ));

                    if (result == null || result.items() == null || result.items().isEmpty()) {
                        break;
                    }

                    for (S item : result.items()) {
                        writeCsvRow(writer, item);
                    }

                    writer.flush();

                    if (expectedTotal < 0 && result.totalItems() >= 0) {
                        expectedTotal = result.totalItems();
                    }

                    if (expectedTotal >= 0
                            && (exportPage + 1) * (long) pageSize >= expectedTotal) {
                        break;
                    }

                    if (result.items().size() < pageSize) {
                        break;
                    }

                    exportPage++;
                }

                Platform.runLater(() -> {
                    if (requestId == requestSequence.get()) {
                        setLoading(false);
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (requestId != requestSequence.get()) {
                        return;
                    }
                    setLoading(false);
                    setPlaceholder(new Label(
                            "Falha ao exportar: " + safeMessage(ex)
                    ));
                });
            }
        });
    }

    private void exportToCSV(Collection<S> records) {
        File file = chooseFile("Exportar dados para CSV", "*.csv", "CSV");
        if (file == null) {
            return;
        }

        List<S> snapshot = records == null
                ? List.of()
                : List.copyOf(records);

        long requestId = requestSequence.incrementAndGet();
        setLoading(true);
        cancelActiveOperation();

        activeOperation = PAGE_EXECUTOR.submit(() -> {
            try (BufferedWriter writer = Files.newBufferedWriter(
                    file.toPath(),
                    StandardCharsets.UTF_8
            )) {
                writer.write("\uFEFF");
                writeCsvHeader(writer);

                for (S item : snapshot) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    writeCsvRow(writer, item);
                }

                writer.flush();

                Platform.runLater(() -> {
                    if (requestId == requestSequence.get()) {
                        setLoading(false);
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (requestId != requestSequence.get()) {
                        return;
                    }
                    setLoading(false);
                    setPlaceholder(new Label(
                            "Falha ao exportar: " + safeMessage(ex)
                    ));
                });
            }
        });
    }

    private void writeCsvHeader(BufferedWriter writer) throws IOException {
        List<TableColumn<S, ?>> columns = getVisibleColumns();

        for (int i = 0; i < columns.size(); i++) {
            writer.write(escapeCsv(columns.get(i).getText()));
            if (i < columns.size() - 1) {
                writer.write(';');
            }
        }
        writer.newLine();
    }

    private void writeCsvRow(BufferedWriter writer, S item) throws IOException {
        List<TableColumn<S, ?>> columns = getVisibleColumns();

        for (int i = 0; i < columns.size(); i++) {
            Object value = columns.get(i).getCellData(item);
            writer.write(escapeCsv(value == null ? "" : value.toString()));
            if (i < columns.size() - 1) {
                writer.write(';');
            }
        }
        writer.newLine();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }

        if (value.indexOf(';') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }

        return value;
    }

    private void exportToExcel(Collection<S> records) {
        File file = chooseFile("Exportar dados para Excel", "*.xlsx", "Excel");
        if (file == null) {
            return;
        }

        List<S> snapshot = records == null
                ? List.of()
                : List.copyOf(records);

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            workbook.setCompressTempFiles(true);

            Sheet sheet = workbook.createSheet("Dados");
            List<TableColumn<S, ?>> columns = getVisibleColumns();

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Font headerFont = workbook.createFont();
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(0);

            for (int i = 0; i < columns.size(); i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns.get(i).getText());
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;

            for (S item : snapshot) {
                Row row = sheet.createRow(rowIndex++);

                for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                    Object value = columns.get(columnIndex).getCellData(item);
                    org.apache.poi.ss.usermodel.Cell cell = row.createCell(columnIndex);

                    if (value instanceof Number number) {
                        cell.setCellValue(number.doubleValue());
                    } else if (value instanceof Boolean bool) {
                        cell.setCellValue(bool);
                    } else {
                        cell.setCellValue(value == null ? "" : value.toString());
                    }
                }
            }

            try (OutputStream output = new FileOutputStream(file)) {
                workbook.write(output);
            }

            workbook.dispose();
        } catch (IOException ex) {
            showExportFailure(ex);
        }
    }

    private File chooseFile(String title, String extension, String description) {
        if (getScene() == null || getScene().getWindow() == null) {
            return null;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(description, extension)
        );

        return chooser.showSaveDialog(getScene().getWindow());
    }

    private void showExportFailure(Exception ex) {
        setPlaceholder(new Label("Falha ao exportar: " + safeMessage(ex)));
    }

    /**
     * Liberta recursos temporários quando a tabela deixa de ser utilizada.
     */
    public void dispose() {
        cancelActiveOperation();
        requestSequence.incrementAndGet();
        searchDebounce.stop();
    }

    private void updatePagerState() {
        if (pageProvider == null) {
            int total = getItems() == null ? 0 : getItems().size();
            resultCountLabel.setText(
                    total + (total == 1 ? " registo" : " registos")
            );
            pageLabel.setText("");
            previousPageButton.setDisable(true);
            nextPageButton.setDisable(true);
            return;
        }

        if (totalItems < 0) {
            long from = pageIndex * (long) pageSize + 1;
            int loaded = getItems() == null ? 0 : getItems().size();
            long to = from + Math.max(0, loaded - 1);

            resultCountLabel.setText(
                    loaded == 0
                            ? "Sem resultados"
                            : from + "–" + to + " · total desconhecido"
            );
        } else {
            long from = totalItems == 0 ? 0 : pageIndex * (long) pageSize + 1;
            int loaded = getItems() == null ? 0 : getItems().size();
            long to = Math.min(totalItems, from + Math.max(0, loaded - 1));

            resultCountLabel.setText(
                    totalItems == 0
                            ? "0 registos"
                            : from + "–" + to + " de " + totalItems
            );
        }

        long pageCount = getPageCount();

        if (pageCount > 0) {
            pageLabel.setText(
                    "Página " + (pageIndex + 1) + " / " + pageCount
            );
        } else if (pageCount == 0) {
            pageLabel.setText("Sem resultados");
        } else {
            pageLabel.setText("Página " + (pageIndex + 1));
        }

        previousPageButton.setDisable(busy.get() || pageIndex <= 0);
        nextPageButton.setDisable(
                busy.get() || (totalItems >= 0 && pageIndex + 1 >= pageCount)
        );
    }

    private long getLastPageIndex() {
        long pages = getPageCount();
        return pages <= 0 ? 0 : pages - 1;
    }

    private void updateLoadingIndicator() {
        loadingIndicator.setVisible(busy.get());
        loadingIndicator.setManaged(busy.get());
    }

    private void setLoadingIndicator() {
        loadingIndicator.setMaxSize(20, 20);
        loadingIndicator.setVisible(false);
        loadingIndicator.setManaged(false);

        busy.addListener((obs, oldValue, newValue) -> {
            updateLoadingIndicator();
            updatePagerState();
        });
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null
                || throwable.getMessage().isBlank()) {
            return throwable == null
                    ? "erro desconhecido"
                    : throwable.getClass().getSimpleName();
        }

        return throwable.getMessage();
    }

    public enum RowDensity {
        COMPACT,
        COMFORTABLE,
        SPACIOUS
    }

    /**
     * Callback usado pelo modo paginado.
     */
    @FunctionalInterface
    public interface PageProvider<S> {
        PageResult<S> load(PageRequest request) throws Exception;
    }

    /**
     * Resultado de uma consulta paginada.
     *
     * @param items registos da página
     * @param totalItems total de registos, ou -1 quando desconhecido
     */
    public record PageResult<S>(List<S> items, long totalItems) {
        public PageResult {
            items = items == null ? List.of() : List.copyOf(items);
            totalItems = Math.max(-1, totalItems);
        }
    }

    /**
     * Pedido de página enviado ao backend.
     */
    public record PageRequest(
            long page,
            int pageSize,
            String filter,
            String sortColumn,
            SortDirection sortDirection
    ) {
        public PageRequest {
            page = Math.max(0, page);
            pageSize = Math.max(MIN_PAGE_SIZE, Math.min(MAX_PAGE_SIZE, pageSize));
            filter = filter == null ? "" : filter;
            sortColumn = sortColumn == null ? "" : sortColumn;
            sortDirection = sortDirection == null ? SortDirection.ASC : sortDirection;
        }

        public long offset() {
            return page * (long) pageSize;
        }
    }

    public enum SortDirection {
        ASC, DESC
    }

    /**
     * Responsável por criação dos pequenos ícones sem depender de classes externas
     * da tabela.
     */
    private static final class IconUtilsForTable {
        private IconUtilsForTable() {
        }

        static Node icon(String name) {
            Label icon = new Label();
            icon.setFocusTraversable(false);
            icon.setMinSize(16, 16);
            icon.setPrefSize(16, 16);
            icon.setMaxSize(16, 16);
            icon.setAlignment(Pos.CENTER);

            switch (name) {
                case "PREVIOUS" -> icon.setText("‹");
                case "NEXT" -> icon.setText("›");
                case "CLEAR" -> icon.setText("×");
                default -> icon.setText("⋯");
            }

            return icon;
        }
    }
}
