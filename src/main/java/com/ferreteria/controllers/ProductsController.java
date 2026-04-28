package com.ferreteria.controllers;

import com.ferreteria.models.Product;
import com.ferreteria.repositories.sqlite.SQLiteProductRepository;
import com.ferreteria.repositories.sqlite.SQLiteSupplierRepository;
import com.ferreteria.services.ProductService;
import com.ferreteria.services.SupplierService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;

public class ProductsController {

    private static final int PAGE_SIZE = 100;

    @FXML private TextField searchField;
    @FXML private TableView<Product> productsTable;
    @FXML private TableColumn<Product, String> colCode;
    @FXML private TableColumn<Product, String> colName;
    @FXML private TableColumn<Product, String> colDescription;
    @FXML private TableColumn<Product, Double> colPrice;
    @FXML private TableColumn<Product, Double> colStock;
    @FXML private TableColumn<Product, Double> colMinStock;
    @FXML private TableColumn<Product, String> colSupplier;
    @FXML private Label summaryTotalProductsLabel;
    @FXML private Label summaryCurrentFilterLabel;
    @FXML private Button btnPrev;
    @FXML private Button btnNext;
    @FXML private Label pageLabel;

    private final ProductService productService;
    private final SupplierService supplierService;
    private final SQLiteProductRepository productRepository;
    private final ObservableList<Product> tableData = FXCollections.observableArrayList();

    private int currentPage = 0;
    private int totalPages = 1;
    private String currentQuery = "";

    public ProductsController() {
        this.productRepository = new SQLiteProductRepository();
        this.productService = new ProductService(productRepository);
        this.supplierService = new SupplierService(new SQLiteSupplierRepository());
    }

    @FXML
    public void initialize() {
        setupTable();
        searchField.setOnAction(e -> onSearch());
        loadPage();
    }

    private void setupTable() {
        colCode.setCellValueFactory(new PropertyValueFactory<>("code"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        colStock.setCellValueFactory(new PropertyValueFactory<>("stock"));
        colMinStock.setCellValueFactory(new PropertyValueFactory<>("minimumStock"));
        colSupplier.setCellValueFactory(new PropertyValueFactory<>("supplierName"));
        productsTable.setItems(tableData);
    }

    private void loadPage() {
        int offset = currentPage * PAGE_SIZE;
        List<Product> products;
        int total;

        if (currentQuery.isBlank()) {
            total = productRepository.countAll();
            products = productRepository.findPage(offset, PAGE_SIZE);
        } else {
            total = productRepository.countSearch(currentQuery);
            products = productRepository.searchPage(currentQuery, offset, PAGE_SIZE);
        }

        totalPages = Math.max(1, (int) Math.ceil((double) total / PAGE_SIZE));
        tableData.setAll(products);
        updatePaginationControls(total);
    }

    private void updatePaginationControls(int total) {
        if (pageLabel != null) {
            pageLabel.setText("Página " + (currentPage + 1) + " de " + totalPages);
        }
        if (btnPrev != null) btnPrev.setDisable(currentPage == 0);
        if (btnNext != null) btnNext.setDisable(currentPage >= totalPages - 1);

        if (summaryTotalProductsLabel != null) {
            summaryTotalProductsLabel.setText(String.valueOf(total));
        }
        if (summaryCurrentFilterLabel != null) {
            summaryCurrentFilterLabel.setText(currentQuery.isBlank() ? "Todos" : "Filtrado");
        }
    }

    @FXML
    private void onSearch() {
        currentQuery = searchField.getText() == null ? "" : searchField.getText().trim();
        currentPage = 0;

        // Si no hay resultados propios, buscar por código exacto en precarga
        if (!currentQuery.isBlank() && productRepository.countSearch(currentQuery) == 0) {
            java.util.Optional<com.ferreteria.models.Product> encontrado =
                    productRepository.findByCode(currentQuery);
            if (encontrado.isPresent() && encontrado.get().isPrecarga()) {
                com.ferreteria.models.Product p = encontrado.get();
                p.setPrecarga(false);
                ProductFormDialog dialog = new ProductFormDialog(p, supplierService.getAllSuppliers());
                dialog.setTitle("Configurar producto");
                dialog.setHeaderText("Producto encontrado en catálogo. Completá precio y stock para activarlo.");
                dialog.showAndWait().ifPresent(configured -> {
                    try {
                        configured.setPrecarga(false);
                        productService.saveProduct(configured);
                        currentQuery = "";
                        searchField.clear();
                    } catch (IllegalArgumentException ex) {
                        showError(ex.getMessage());
                    }
                });
            }
        }

        loadPage();
    }

    @FXML
    private void onResetFilter() {
        searchField.clear();
        currentQuery = "";
        currentPage = 0;
        loadPage();
    }

    @FXML
    private void onPrevPage() {
        if (currentPage > 0) {
            currentPage--;
            loadPage();
        }
    }

    @FXML
    private void onNextPage() {
        if (currentPage < totalPages - 1) {
            currentPage++;
            loadPage();
        }
    }

    @FXML
    private void onNewProduct() {
        ProductFormDialog dialog = new ProductFormDialog(null, supplierService.getAllSuppliers());
        dialog.showAndWait().ifPresent(product -> {
            try {
                productService.saveProduct(product);
                loadPage();
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
            }
        });
    }

    @FXML
    private void onEditProduct() {
        Product selected = productsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Seleccione un producto para editar.");
            return;
        }
        ProductFormDialog dialog = new ProductFormDialog(selected, supplierService.getAllSuppliers());
        dialog.showAndWait().ifPresent(product -> {
            try {
                productService.saveProduct(product);
                loadPage();
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
            }
        });
    }

    @FXML
    private void onDeleteProduct() {
        Product selected = productsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Seleccione un producto para eliminar.");
            return;
        }
        try {
            productService.deleteProduct(selected);
            loadPage();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
