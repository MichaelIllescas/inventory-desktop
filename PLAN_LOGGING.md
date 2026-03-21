# Plan de Logging para JavaFX (Implementación posterior)

## Objetivo

Registrar de forma clara y trazable los errores y eventos críticos del sistema, especialmente en el flujo de ventas, para diagnosticar por qué en algunos casos la app sale del panel de ventas y termina en dashboard.

## Contexto actual del proyecto

- Existe manejo global de excepciones en `App.java` mediante `Thread.setDefaultUncaughtExceptionHandler(...)`.
- Ya se escribe un log de errores fatales en archivo (`error.log` en `%LOCALAPPDATA%\Sistema de Inventario\`).
- La navegación entre pantallas se concentra en `MainController` con `loadView(...)`.
- El flujo de ventas está en `SalesController`, con operaciones críticas en `confirmAndRegisterSale()` y en acceso a datos vía `SaleService`/`SQLiteSaleRepository`.

## Problema a cubrir

Actualmente, en ventas se captura `IllegalArgumentException`, pero errores de otro tipo (runtime/SQL envueltos, etc.) pueden no quedar correctamente trazados en el punto funcional donde ocurren. Se necesita logging funcional (por flujo) ademas del logging fatal global.

## Estrategia propuesta

### 1) Crear logger central de aplicacion

Crear una utilidad de logging (ejemplo: `AppLogger`) sin dependencias externas por ahora.

Responsabilidades:

- Escribir logs en archivo dedicado (por ejemplo: `app.log`).
- Soportar niveles: `INFO`, `WARN`, `ERROR`.
- Permitir log con excepcion (`Throwable`) y stacktrace.
- Formato consistente por linea:
  - timestamp
  - nivel
  - modulo/pantalla
  - accion
  - operationId (si existe)
  - mensaje

Formato sugerido (texto plano):

`2026-03-20 10:35:12 | ERROR | SalesController | registerSale | op=8f3a2c | Error al registrar venta | java.lang.RuntimeException: ...`

### 2) Instrumentar navegacion en MainController

Agregar logs en `loadView(String fxmlName)`:

- Antes de cargar la vista: `INFO` (vista solicitada).
- Al cargar exitosamente: `INFO`.
- En `catch(IOException e)`: `ERROR` con stacktrace y nombre de vista.

Objetivo: confirmar si realmente hubo una navegacion explicita al dashboard o si la excepcion produjo un efecto secundario visual.

### 3) Instrumentar flujo de ventas en SalesController

Agregar logs en puntos clave:

- `initialize()` (entrada/salida).
- `onScanFieldKeyPressed(...)` (evento Enter, estado del campo).
- `addProductByCode(...)`:
  - codigo recibido
  - producto encontrado/no encontrado
- `confirmAndRegisterSale()`:
  - inicio de operacion
  - cantidad de items
  - total
  - medio de pago
  - confirmacion de usuario
  - resultado exitoso/fallido

Manejo de errores:

- Mantener mensajes de negocio para usuario (alerts actuales).
- Ampliar captura para registrar tambien excepciones no previstas (`Exception`/`RuntimeException`) con detalle tecnico en log.

### 4) Instrumentar capa de servicio y repositorio

En `SaleService`:

- Log al inicio de `registerSale(...)`.
- Log antes/despues de `DatabaseManager.runInTransaction(...)`.
- Log de validaciones de stock insuficiente.

En `SQLiteSaleRepository`:

- Log en errores SQL antes de envolver en `RuntimeException`.
- Incluir contexto minimo: metodo (`saveSale`, `saveSaleItems`), cantidad de items, `saleId` cuando aplique.

### 5) Trazabilidad por operacion (operationId)

Generar un `operationId` por intento de registrar venta (UUID corto o timestamp+random) en `SalesController`, y propagarlo en logs de:

- `SalesController`
- `SaleService`
- `SQLiteSaleRepository`

Objetivo: reconstruir toda la secuencia de una venta en una sola busqueda de log.

### 6) Politica de datos en logs

Registrar:

- id de producto/codigo
- cantidad de items
- total y medio de pago
- nombre de pantalla y accion

Evitar registrar:

- datos sensibles de usuario
- informacion innecesaria o extremadamente verbosa

## Plan de implementacion (fases)

### Fase 1 - Base minima

- Crear `AppLogger`.
- Agregar logs de navegacion en `MainController`.
- Agregar logs de flujo y errores en `SalesController`.

### Fase 2 - Capa de negocio y datos

- Agregar logs en `SaleService` y `SQLiteSaleRepository`.
- Incluir `operationId` de punta a punta.

### Fase 3 - Validacion

- Ejecutar flujo normal de venta y revisar consistencia de logs.
- Forzar escenarios de error:
  - producto inexistente
  - stock insuficiente
  - error de BD simulado
- Confirmar que cada fallo queda con stacktrace y contexto funcional.

## Criterios de aceptacion

- Ante cualquier fallo en ventas, existe al menos un registro `ERROR` con:
  - pantalla/modulo
  - accion
  - mensaje
  - stacktrace
- Se puede seguir una operacion completa de venta por `operationId`.
- Los logs permiten distinguir:
  - error de UI/controlador
  - error de negocio
  - error de persistencia/SQL
- El usuario sigue viendo mensajes de error amigables, sin filtrar detalles tecnicos.

## Ubicacion sugerida de archivos de log

- `error.log` (fatal/global): mantener el actual.
- `app.log` (operacional/funcional): nuevo archivo para seguimiento de flujo.

Directorio sugerido:

- Windows: `%LOCALAPPDATA%\Sistema de Inventario\`

## Nota final

Este plan prioriza diagnostico rapido con bajo impacto en arquitectura actual. En una etapa futura, se puede migrar a un framework de logging (SLF4J + Logback) si se requiere rotacion automatica de archivos, formatos avanzados o configuracion por entorno.
