# Plan de Implementación: Cuentas Corrientes de Clientes

## 1. Objetivo

Agregar cuentas corrientes sin romper el funcionamiento actual del sistema, separando correctamente:

- **Venta (devengado)**: lo facturado/vendido.
- **Cobro (caja real)**: dinero efectivamente ingresado.

Esto evita que reportes y netos queden inconsistentes cuando hay ventas fiadas o pagos parciales.

---

## 2. Estado Actual del Proyecto (base sobre la que se diseña)

Actualmente los reportes calculan:

- **Ingresos**: total de ventas del período.
- **Neto**: ingresos - gastos.

No existe aún distinción entre:

- venta a crédito (cuenta corriente),
- cobro posterior de esa deuda.

Por lo tanto, al incorporar cuenta corriente hay que ajustar el modelo de datos y reportes para no mezclar devengado con caja.

---

## 3. Diseño Funcional Propuesto

## 3.1 Nuevo módulo: Clientes

CRUD de clientes con campos mínimos:

- nombre (obligatorio)
- teléfono
- dirección
- límite de crédito (opcional)
- activo/inactivo

## 3.2 Venta en cuenta corriente

En pantalla de ventas, agregar medio de pago:

- `CUENTA_CORRIENTE`

Reglas:

- Si el medio es cuenta corriente, el cliente es obligatorio.
- La venta se registra normal (incluye descuento de stock).
- Se genera un movimiento de cuenta corriente tipo **DEBITO** por el total de la venta.

## 3.3 Cobro de deuda

Nuevo módulo: **Cuentas Corrientes**.

Flujo:

1. Buscar cliente.
2. Ver saldo actual y detalle de movimientos.
3. Registrar pago (monto, fecha/hora, medio, observación).
4. El pago genera movimiento tipo **CREDITO**.

## 3.4 Pagos parciales

Un pago puede cancelar una venta en forma total o parcial.

Estados de cada venta a crédito:

- `PENDIENTE`
- `PARCIAL`
- `PAGADA`

Regla sugerida para asignación automática:

- FIFO (cancelar primero las ventas más antiguas).

Opcional futuro:

- asignación manual por venta desde la UI.

## 3.5 Resumen para enviar al cliente

Generar comprobantes en PDF:

- **Estado de cuenta** (por cliente y rango de fechas).
- **Recibo de pago** (por pago individual).

Contenido del estado de cuenta:

- datos del cliente
- saldo inicial
- detalle cronológico (debe/haber/saldo acumulado)
- totales del período
- saldo final

---

## 4. Modelo de Datos Propuesto

## 4.1 Tabla `customers`

Campos recomendados:

- `id INTEGER PRIMARY KEY AUTOINCREMENT`
- `name TEXT NOT NULL`
- `phone TEXT`
- `address TEXT`
- `credit_limit REAL NOT NULL DEFAULT 0`
- `active INTEGER NOT NULL DEFAULT 1`
- `created_at TEXT NOT NULL`

## 4.2 Extensión de `sales`

Agregar:

- `customer_id INTEGER NULL` (FK a `customers.id`)

Mantener `payment_method` y sumar valor:

- `CUENTA_CORRIENTE`

## 4.3 Tabla `customer_account_movements`

Libro de movimientos contables de cuenta corriente:

- `id`
- `customer_id`
- `date`
- `type` (`DEBITO`, `CREDITO`, `AJUSTE`)
- `amount`
- `sale_id` (nullable)
- `payment_id` (nullable)
- `notes`

## 4.4 Tabla `customer_payments`

Cabecera de pagos:

- `id`
- `customer_id`
- `date`
- `amount`
- `payment_method`
- `notes`

## 4.5 Tabla `customer_payment_applications`

Aplicación de pago a ventas (soporte parcial):

- `id`
- `payment_id`
- `sale_id`
- `applied_amount`

Permite trazabilidad exacta de qué pago canceló qué venta.

---

## 5. Compatibilidad con Bases de Datos Existentes

## 5.1 Qué pasa con los datos actuales

Las ventas históricas existentes quedarán:

- con `customer_id = NULL`,
- sin deuda generada automáticamente.

Eso significa:

- no se rompe nada,
- no se “inventan” cuentas corrientes retroactivas.

## 5.2 Estrategia de migración segura

Aplicar migraciones idempotentes en `DatabaseManager`:

- `CREATE TABLE IF NOT EXISTS ...`
- chequeo de columnas con `PRAGMA table_info(...)`
- `ALTER TABLE ... ADD COLUMN ...` solo si falta
- creación de índices `IF NOT EXISTS`

## 5.3 Regla operativa recomendada

Cuenta corriente aplica **desde la fecha de despliegue**.

Opcional futuro:

- herramienta manual para reclasificar ventas históricas a un cliente y generar deuda retroactiva controlada.

---

## 6. Ajustes en Reportes (clave contable)

Se deben mostrar dos visiones en paralelo:

1. **Devengado**
- Ventas del período (incluye cuenta corriente)
- Neto devengado = Ventas - Gastos

2. **Caja**
- Cobros reales del período (contado + pagos de cuenta corriente)
- Neto de caja = Cobros reales - Gastos

Además:

- saldo inicial de cuenta corriente
- débitos del período
- créditos del período
- saldo final

De esta forma se evita confundir “vendido” con “cobrado”.

---

## 7. Reglas de Negocio Importantes

1. No borrar movimientos contables físicos (preferir anulación con contramovimiento).
2. Si se anula una venta en cuenta corriente, revertir su débito.
3. Si se anula un pago, revertir su crédito y aplicaciones.
4. Definir política de sobrepago:
- bloquear, o
- permitir saldo a favor del cliente (recomendado).
5. Validar cliente obligatorio al vender en cuenta corriente.
6. (Opcional) alertar si supera límite de crédito.

---

## 8. Cambios Técnicos por Capa

## 8.1 Base de datos

- Nuevas tablas: `customers`, `customer_account_movements`, `customer_payments`, `customer_payment_applications`
- Nueva columna: `sales.customer_id`
- Nuevos índices por `customer_id`, `date`, `sale_id`

## 8.2 Modelos Java

Crear modelos:

- `Customer`
- `CustomerAccountMovement`
- `CustomerPayment`
- `CustomerPaymentApplication`
- `CustomerStatementRow` (para estado de cuenta)

## 8.3 Repositorios/Servicios

Nuevos repositorios:

- `CustomerRepository`
- `CustomerAccountRepository`
- `CustomerPaymentRepository`

Servicios:

- `CustomerService`
- `CurrentAccountService`

Integración con `SaleService`:

- al confirmar venta CC, crear débito en cuenta corriente.

## 8.4 UI (JavaFX)

Nuevas pantallas:

- `customers-view.fxml`
- `current-account-view.fxml`

Cambios en ventas:

- incluir medio `CUENTA_CORRIENTE`
- selector de cliente cuando corresponda

Cambios en reportes:

- métricas de devengado y caja en resumen
- incluir vista de pagos de cuenta corriente

---

## 9. Plan de Implementación por Fases

## Fase 1: Datos y migraciones

- crear tablas/columnas nuevas
- validar migración con BD existente
- pruebas de arranque sin pérdida de datos

## Fase 2: Venta con cuenta corriente

- ampliar medio de pago
- exigir cliente en venta a crédito
- generar débito automático

## Fase 3: Cobros y parciales

- pantalla de cuenta corriente
- registrar pago
- aplicar pago a ventas (FIFO)
- actualizar estados pendiente/parcial/pagada

## Fase 4: Reportes contables correctos

- separar devengado vs caja
- mostrar neto devengado y neto de caja
- incluir saldo corriente de clientes

## Fase 5: Comprobantes PDF

- resumen de cuenta por cliente
- recibo de pago
- descarga/exportación lista para envío

---

## 10. Criterios de Aceptación

1. Una venta en cuenta corriente no incrementa caja hasta registrar pago.
2. Un pago parcial reduce deuda y deja saldo pendiente correcto.
3. Reportes muestran en forma explícita:
- ventas devengadas,
- cobros reales,
- neto devengado,
- neto de caja.
4. BD anterior abre sin errores y sin pérdida de datos.
5. Se puede exportar PDF de estado de cuenta por cliente.

---

## 11. Nota sobre `NULL` y vacío en la BD

Para este diseño:

- `NULL` significa “sin dato/asociación” (ej. venta vieja sin cliente).
- `''` (vacío) es un valor de texto existente.

No son equivalentes en SQL/SQLite, y se consultan distinto (`IS NULL` vs `= ''`).

