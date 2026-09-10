# Módulo de Presupuestos

Documentación del módulo incorporado en **v3.2**. Disponible en las ediciones **Plus** y **Completa**.

---

## 1. Qué es un presupuesto acá

Un documento informativo que se le entrega al cliente con el detalle de lo cotizado y su total.

Deliberadamente **no** hace nada de esto:

- No descuenta stock.
- No genera una venta ni un movimiento de caja.
- No impacta en reportes ni en cuentas corrientes.

Si el cliente acepta, la venta se carga aparte por el módulo de Ventas. Convertir un presupuesto en venta con un botón queda como mejora futura.

---

## 2. Habilitación por edición

El módulo se activa con la clave `module.quotes` de `license.properties`:

| Edición | `module.quotes` | Ítem en el sidebar |
|---------|-----------------|--------------------|
| `basic` | `false` | Oculto |
| `plus` | `true` | Visible |
| `complete` | `true` | Visible |

`LicenseService.isQuotesEnabled()` es el único punto de verdad. `MainController` lo consulta en dos lugares:

- `applyLicensedModules()`: oculta el botón del sidebar (`setVisible` + `setManaged`, para que no deje un hueco).
- `showQuotes()` y `openSection()`: si el módulo está apagado redirigen al dashboard, por si alguien llega a la vista por otro camino.

---

## 3. Modelo de datos

### 3.1 Tabla `quotes`

| Columna | Tipo | Nota |
|---------|------|------|
| `id` | INTEGER PK | Es el número de presupuesto que ve el cliente |
| `date` | TEXT NOT NULL | ISO de emisión; no cambia al editar |
| `customer_id` | INTEGER | FK a `customers`, admite null |
| `customer_name` | TEXT | Copia del nombre al momento de emitir |
| `valid_days` | INTEGER NOT NULL (15) | Días de validez |
| `notes` | TEXT | Observaciones para el cliente |
| `total` | REAL NOT NULL | Suma de los subtotales, calculada al guardar |

`customer_name` se guarda aunque exista `customer_id`. Sirve para dos casos: presupuestos sin cliente ("Consumidor final") y para que el listado no tenga que hacer un join.

Para el PDF, en cambio, el cliente se **relee de la base** por `customer_id`, así el documento sale siempre con los datos de contacto actualizados.

### 3.2 Tabla `quote_items`

| Columna | Tipo | Nota |
|---------|------|------|
| `id` | INTEGER PK | |
| `quote_id` | INTEGER NOT NULL | FK con `ON DELETE CASCADE` |
| `product_id` | INTEGER | **Null = línea manual** |
| `code` | TEXT | Código del producto, vacío en manuales |
| `description` | TEXT NOT NULL | |
| `quantity` | REAL NOT NULL | Admite decimales |
| `price` | REAL NOT NULL | Puede ser **negativo**: así se cargan los descuentos |
| `subtotal` | REAL NOT NULL | Puede diferir de `quantity * price` si se sobrescribió |

No hay FK a `products` a propósito: si mañana se borra un producto, el presupuesto histórico tiene que seguir legible con su descripción y precio de entonces.

### 3.3 Migración

`DatabaseManager.migrateAddQuotes()` crea ambas tablas con `CREATE TABLE IF NOT EXISTS`. Las bases existentes se migran solas al abrir la app, sin pérdida de datos.

La columna `tax_id` de `customers` (DNI/CUIT) se agrega en `migrateAddCustomerTaxId()`, que verifica con `PRAGMA table_info` antes del `ALTER TABLE`.

---

## 4. Descuentos y recargos

No hay un campo "descuento" en la cabecera. Un descuento **es un ítem más**, con importe negativo; un recargo es lo mismo en positivo. Ventajas:

- El total sigue siendo la suma de los subtotales, sin reglas especiales.
- Se pueden cargar varios, con la descripción que se quiera ("Descuento por pago contado", "Recargo por financiación").
- Quedan visibles en el PDF como una línea más, que es lo que el cliente espera ver.

Los botones **Agregar descuento** y **Agregar recargo** piden el monto y le ponen el signo correspondiente (`onAddAdjustment(boolean)`). También se puede editar a mano el precio de cualquier fila y ponerlo negativo.

Única restricción, en `QuoteService.validate()`: **el total no puede quedar negativo**.

---

## 5. Recálculo de subtotales

`QuoteLineItem` recalcula `subtotal = cantidad * precio` cuando cambia alguno de los dos, salvo que el usuario haya sobrescrito el subtotal a mano (para descuentos por línea o ajustes). Eso lo marca la bandera `subtotalOverridden`.

Ojo con los dos setters, que es donde estuvo un bug:

- `setSubtotal(double)`: uso del usuario. **Marca** la línea como sobrescrita y apaga el recálculo.
- `restoreSubtotal(double)`: uso del repositorio al leer de la base. Marca la línea como sobrescrita **solo si** el valor guardado difiere de `cantidad * precio`.

Si al cargar de la base se usa `setSubtotal()`, todas las líneas quedan congeladas y editar la cantidad no actualiza el total.

---

## 6. Archivos del módulo

```
models/Quote.java                          # Cabecera del presupuesto
models/QuoteLineItem.java                  # Línea, con propiedades JavaFX para la tabla
repositories/QuoteRepository.java          # Contrato
repositories/sqlite/SQLiteQuoteRepository.java
services/QuoteService.java                 # Validaciones y cálculo del total
controllers/QuotesController.java          # Listado, buscador y columna de acciones
controllers/QuoteFormDialog.java           # Alta y edición
util/QuotePdfExporter.java                 # Exportación a PDF
resources/ui/quotes-view.fxml              # Vista del listado
```

El alta/edición no tiene FXML: se arma por código, como `ProductSelectionDialog`.

### Persistencia

`save()` y `update()` corren dentro de una transacción (`setAutoCommit(false)` + `commit`, con `rollback` ante error), así un presupuesto nunca queda guardado a medias, con cabecera pero sin ítems.

`update()` **reemplaza las líneas completas**: borra todas las de ese presupuesto y las inserta de nuevo. Es más simple que diferenciar altas, bajas y modificaciones, y al estar en transacción no tiene riesgo.

---

## 7. PDF

`QuotePdfExporter` replica el formato de `CurrentAccountPdfExporter` (estado de cuenta) para que los documentos del sistema se vean como una familia:

1. Logo y datos del negocio, tomados de Configuración (`AppSettings`).
2. Título, número de presupuesto y fecha de emisión.
3. Datos del cliente: nombre, teléfono, dirección y **DNI/CUIT si lo tiene cargado**. Si no lo tiene, esa celda no se muestra.
4. Tabla de ítems. Los importes negativos van en rojo.
5. **Total al pie**, debajo de la tabla.
6. Observaciones, si hay.
7. Leyenda: sin valor fiscal y precios sujetos a modificación pasada la validez.

---

## 8. Interfaz

**Listado**: buscador por nombre de cliente (coincidencia parcial) o número de presupuesto (exacto, para que "7" no traiga el 17 y el 27). El filtro se mantiene después de crear, editar o eliminar. Columna de acciones con **↓ PDF**, **Editar** y **Eliminar**.

**Alta/edición**: el selector de cliente filtra en vivo por nombre, teléfono, dirección o DNI/CUIT — con muchos clientes un combo plano no sirve. Si queda una sola coincidencia la selecciona sola, y debajo muestra teléfono y dirección para confirmar que es el correcto.

El diálogo es una ventana aparte y **no toma `styles.css`**, por eso el centrado de columnas va por código y no por CSS.

---

## 9. Pendientes / mejoras posibles

- Convertir un presupuesto aceptado en venta.
- Descuento por porcentaje además de monto fijo.
- Estados (vigente / aceptado / rechazado / vencido) y filtro por estado.
- Duplicar un presupuesto existente como base de uno nuevo.
- Si el volumen crece mucho, pasar el filtro del listado de memoria a SQL.
