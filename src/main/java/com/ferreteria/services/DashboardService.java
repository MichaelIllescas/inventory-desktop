package com.ferreteria.services;

import com.ferreteria.models.Sale;
import com.ferreteria.models.SalesByDay;
import com.ferreteria.repositories.SaleRepository;
import com.ferreteria.repositories.sqlite.SQLiteProductRepository;
import com.ferreteria.repositories.sqlite.SQLiteSaleRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DashboardService {

    private final SQLiteProductRepository productRepository;
    private final SaleRepository saleRepository;

    public DashboardService() {
        this.productRepository = new SQLiteProductRepository();
        this.saleRepository = new SQLiteSaleRepository();
    }

    public int getTotalProducts() {
        return productRepository.countAll();
    }

    public int getLowStockCount() {
        return productRepository.countLowStock();
    }

    public double getTodaySalesTotal() {
        return saleRepository.getTodaySalesTotal();
    }

    public double getMonthSalesTotal() {
        return saleRepository.getMonthSalesTotal();
    }

    public List<SalesByDay> getSalesLastDays(int days) {
        return saleRepository.getSalesLastDays(days);
    }

    /** Inserta ventas de ejemplo de los últimos 7 días para ver el gráfico con varias barras. */
    public void loadDemoSalesForChart() {
        double[] totals = { 450, 1200, 780, 2100, 1500, 950, 3200 };
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            LocalDateTime dt = day.atTime(12, 0);
            Sale sale = new Sale();
            sale.setDate(dt.format(fmt));
            sale.setTotal(totals[6 - i]);
            saleRepository.saveSale(sale);
        }
    }
}
