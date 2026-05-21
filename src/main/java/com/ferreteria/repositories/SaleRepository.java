package com.ferreteria.repositories;

import com.ferreteria.models.CustomerCurrentAccountReportRow;
import com.ferreteria.models.ProductSalesReport;
import com.ferreteria.models.Sale;
import com.ferreteria.models.SaleDetailRow;
import com.ferreteria.models.SaleItem;
import com.ferreteria.models.SalesByDay;

import java.util.List;

public interface SaleRepository {

    Sale saveSale(Sale sale);

    void saveSaleItems(int saleId, List<SaleItem> items);

    double getTodaySalesTotal();

    double getMonthSalesTotal();

    List<SalesByDay> getSalesLastDays(int days);

    double getSalesTotalInRange(String dateFrom, String dateTo);

    double getCollectedTotalInRange(String dateFrom, String dateTo);

    double getCurrentAccountPaymentsTotalInRange(String dateFrom, String dateTo);

    double getCurrentAccountSalesTotalInRange(String dateFrom, String dateTo);

    List<SalesByDay> getSalesByDayInRange(String dateFrom, String dateTo);

    List<ProductSalesReport> getTopProductsInRange(String dateFrom, String dateTo, int limit);

    List<SaleDetailRow> getSaleDetailsInRange(String dateFrom, String dateTo);

    List<CustomerCurrentAccountReportRow> getCurrentAccountReportByCustomer(String dateFrom, String dateTo);

    List<SaleItem> getSaleItemsBySaleId(int saleId);

    void deleteSale(int saleId);
}
