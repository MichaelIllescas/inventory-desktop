package com.ferreteria.services;

import com.ferreteria.models.CustomerCurrentAccountReportRow;
import com.ferreteria.models.ProductSalesReport;
import com.ferreteria.models.SaleDetailRow;
import com.ferreteria.models.SalesByDay;
import com.ferreteria.repositories.SaleRepository;
import com.ferreteria.repositories.sqlite.SQLiteSaleRepository;

import java.time.LocalDate;
import java.util.List;

public class ReportService {

    private final SaleRepository saleRepository = new SQLiteSaleRepository();

    public double getSalesTotalInRange(String dateFrom, String dateTo) {
        return saleRepository.getSalesTotalInRange(dateFrom, dateTo);
    }

    public double getCollectedTotalInRange(String dateFrom, String dateTo) {
        return saleRepository.getCollectedTotalInRange(dateFrom, dateTo);
    }

    public double getCurrentAccountPaymentsTotalInRange(String dateFrom, String dateTo) {
        return saleRepository.getCurrentAccountPaymentsTotalInRange(dateFrom, dateTo);
    }

    public double getCurrentAccountSalesTotalInRange(String dateFrom, String dateTo) {
        return saleRepository.getCurrentAccountSalesTotalInRange(dateFrom, dateTo);
    }

    public List<SalesByDay> getSalesByDayInRange(String dateFrom, String dateTo) {
        return saleRepository.getSalesByDayInRange(dateFrom, dateTo);
    }

    public List<ProductSalesReport> getTopProductsInRange(String dateFrom, String dateTo, int limit) {
        return saleRepository.getTopProductsInRange(dateFrom, dateTo, limit);
    }

    public List<SaleDetailRow> getSaleDetailsInRange(String dateFrom, String dateTo) {
        return saleRepository.getSaleDetailsInRange(dateFrom, dateTo);
    }

    public List<CustomerCurrentAccountReportRow> getCurrentAccountReportByCustomer(String dateFrom, String dateTo) {
        return saleRepository.getCurrentAccountReportByCustomer(dateFrom, dateTo);
    }

    /** Fecha de hoy en YYYY-MM-DD */
    public static String today() {
        return LocalDate.now().toString();
    }

    /** Fecha de hace N días en YYYY-MM-DD */
    public static String daysAgo(int n) {
        return LocalDate.now().minusDays(n).toString();
    }
}
