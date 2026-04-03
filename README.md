# Sistema de Inventario

Aplicación de escritorio para gestión de inventario, ventas, proveedores y reportes. Desarrollada en **Java 17**, **JavaFX** y **SQLite**.

---

## Características

- **Dashboard**: total de productos, productos con bajo stock, ventas del día y del mes (en $), y gráfico de ventas de los últimos 7 días.
- **Productos**: alta, edición, baja y listado con búsqueda; código, nombre, precio, stock mínimo y proveedor. Soporta **stock decimal** (ej. kg de carne, metros de cable).
- **Ventas**:
  - Registro con selección de productos y **cantidades decimales**, atajos con lector de código de barras.
  - **Precio unitario y subtotal editables** directamente en la tabla (el subtotal se puede sobrescribir independientemente del precio unitario, por ejemplo para aplicar descuentos).
  - **Total a cobrar** editable con formato de miles (`1.500,00`); se edita con doble clic y muestra la diferencia ("Ajuste") si se modifica manualmente.
  - **Panel de resumen** lateral con cantidad de productos distintos, unidades totales y total calculado.
  - Confirmación rápida con **Enter** y limpieza de la venta con un click.
  - **Producto "Varios / Sin código"** (`__VARIOS__`): se crea automáticamente al iniciar la app, no afecta el inventario, precio comienza en 0 y se abre para editar al agregarlo. Incluye **código de barras Code 128** en la pantalla de ventas para escanearlo directamente con un lector.
- **Medios de pago**: selección de **Efectivo, Transferencia, Débito y Crédito** al registrar la venta; cada venta queda asociada a su medio de pago.
- **Proveedores**: CRUD de proveedores (nombre, teléfono, email, dirección).
- **Inventario**: vista de stock y ajuste de cantidades con diálogos dedicados.
- **Reportes**:
  - **Por día**: totales discriminados por medio de pago (Total, Efectivo, Transferencia, Débito, Crédito) y promedio diario.
  - **Productos más vendidos**: cantidad vendida, monto total y total de unidades.
  - **Con detalle**: cada ítem con precio cobrado, subtotal, **total de la venta** y medio de pago. Permite eliminar ventas (devuelve el stock).
  - Barra de resumen con totales y métricas clave en todos los reportes.

La base de datos es **SQLite**; en instalación los datos se guardan en la carpeta de usuario (por usuario de Windows). La aplicación se distribuye como **instalador .exe** para Windows (sin necesidad de instalar Java en el cliente).

---

## Requisitos para desarrollo

- **JDK 17** o superior (Eclipse Temurin, Adoptium, etc.).
- **Maven 3.6+**.
- Variable de entorno **`JAVA_HOME`** apuntando al JDK.

---

## Estructura del proyecto

```
DESKTOP INVENTIORY/
├── src/main/java/com/ferreteria/
│   ├── App.java                    # Punto de entrada
│   ├── database/
│   │   └── DatabaseManager.java    # Conexión SQLite y esquema
│   ├── controllers/                # Controladores FXML (vistas)
│   ├── models/                     # Modelos de datos
│   ├── repositories/               # Acceso a datos (SQLite)
│   ├── services/                   # Lógica de negocio
│   └── util/                       # Utilidades (ej. Code128Generator)
├── src/main/resources/
│   ├── database/
│   │   └── schema.sql              # Definición de tablas
│   ├── images/                     # Iconos y logos (app, Imperial Net)
│   └── ui/                         # Vistas FXML y estilos CSS
├── build-installer.bat             # Script para generar el .exe
├── Ejecutar-crear-instalador.bat   # Lanza el script y mantiene la ventana abierta
├── pom.xml
├── README.md
├── INSTALADOR.md                   # Instrucciones para generar el instalador
└── .gitignore
```

- **`data/`** (en la raíz): en desarrollo, con `-Dsistema.inventario.dev=true`, la base se usa aquí (`data/inventory.db`). No se incluye en el instalador. Los archivos `data/seed_*.sql` no los usa la aplicación; puedes eliminarlos si no los necesitas para pruebas manuales.
- **`target/`**: generado por Maven y por el script del instalador; no se sube a Git.

---

## Ejecutar en desarrollo

### Con Maven (usa la carpeta `data` del proyecto)

```bash
mvn javafx:run
```

El plugin está configurado con `-Dsistema.inventario.dev=true`, así que la base de datos se crea/usa en `./data/inventory.db`.

### Desde el IDE

- Clase principal: `com.ferreteria.App`.
- Añade la opción de VM: **`-Dsistema.inventario.dev=true`** si quieres usar la carpeta `data` del proyecto; si no, la app usará `%LOCALAPPDATA%\Sistema de Inventario\data`.

---

## Generar el instalador (.exe)

Requisitos: **JDK 17+** (con `jpackage` y `jlink`), **Maven**, y la primera vez conexión a internet (descarga de JavaFX).

1. Abrir CMD en la raíz del proyecto.
2. Ejecutar:
   ```bat
   build-installer.bat
   ```
   o, para que la ventana no se cierre al terminar:
   ```bat
   Ejecutar-crear-instalador.bat
   ```
3. El instalador se genera en:
   ```text
   target\installer\Sistema de Inventario-1.0.0.exe
   ```

El script hace en resumen:

1. **Maven** (`mvn clean package -Pinstaller`): compila y deja JARs en `target/app`.
2. **JavaFX**: usa el JDK si ya trae JavaFX o descarga jmods una vez en `target/fx-jmods`.
3. **jlink**: arma un runtime con Java + JavaFX en `target/runtime-jre`.
4. **jpackage**: genera el .exe con ese runtime e icono (si existe `src/main/resources/images/logo-nuevo.ico`).

Más detalles en [INSTALADOR.md](INSTALADOR.md).

---

## Dónde se guardan los datos

| Modo        | Ubicación |
|------------|-----------|
| **Instalado** (cliente) | `%LOCALAPPDATA%\Sistema de Inventario\data\inventory.db` |
| **Desarrollo** (con `-Dsistema.inventario.dev=true`) | `./data/inventory.db` |

Cada usuario de Windows tiene su propia carpeta en `%LOCALAPPDATA%`, así que cada uno tiene su propia base. La aplicación **no** incluye datos de prueba en el instalador; al instalar, la base se crea vacía.

Las tablas principales usan IDs `INTEGER PRIMARY KEY AUTOINCREMENT` (enteros de 64 bits en SQLite) y en Java se modelan como `Integer`, por lo que no hay riesgo práctico de “quedarse sin IDs” incluso con años de uso intensivo.

---

## Tecnologías

- **Java 17**
- **JavaFX 21** (UI)
- **SQLite** (JDBC)
- **Maven** (build y perfil `installer` para el .exe)

---

## Créditos

- **Desarrollado por** [Imperial Net](https://imperial-net.com)

---

## Licencia

Uso según los términos acordados con el titular del proyecto.
