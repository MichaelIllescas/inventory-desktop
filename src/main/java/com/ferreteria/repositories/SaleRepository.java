package com.ferreteria.repositories;

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

    /** Total de ventas entre dos fechas (incluidas). Fechas en formato YYYY-MM-DD. */
    double getSalesTotalInRange(String dateFrom, String dateTo);

    /** Ventas agrupadas por día en el rango. */
    List<SalesByDay> getSalesByDayInRange(String dateFrom, String dateTo);

    /** Productos más vendidos en el rango (cantidad y monto). */
    List<ProductSalesReport> getTopProductsInRange(String dateFrom, String dateTo, int limit);

    /** Detalle de cada ítem de cada venta en el rango (para reporte ventas con detalle). */
    List<SaleDetailRow> getSaleDetailsInRange(String dateFrom, String dateTo);

    /** Ítems de una venta (para restaurar stock al anular). */
    List<SaleItem> getSaleItemsBySaleId(int saleId);

    /** Elimina una venta (CASCADE borra sus ítems). Llamar después de restaurar stock. */
    void deleteSale(int saleId);
}
