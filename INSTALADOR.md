# Generar el instalador .exe

Resumen rápido. Para requisitos y estructura del proyecto ver [README.md](README.md).

## Requisitos

- **JDK 17 o 21** con `JAVA_HOME` configurado.
- **Maven** en el PATH (o `MAVEN_HOME`).
- **Conexión a internet** la primera vez (descarga de JavaFX jmods).

## Ediciones

El instalador se genera para una edicion. Cada una activa distintos modulos:

| Edicion | Cuentas corrientes | Presupuestos |
|---------|--------------------|--------------|
| `basic` | No | No |
| `plus` | No | Si |
| `complete` | Si | Si |

La definicion de cada una esta en `installer/licenses/<edicion>.properties`. El script copia ese archivo como `license.properties` dentro del .exe, y la app lo lee al iniciar.

## Pasos

1. Abrir CMD en la carpeta del proyecto.
2. Ejecutar: **`build-installer.bat <edicion>`**, por ejemplo:  
   - `build-installer.bat basic`  
   - `build-installer.bat plus`  
   - `build-installer.bat complete`  
   Sin parametro usa la variable `INVENTORY_EDITION` y, si tampoco existe, **`basic`**.  
   (O **`Ejecutar-crear-instalador.bat`** para que la ventana no se cierre.)
3. Al terminar, el instalador queda en:  
   **`target\installer\Sistema de Inventario-<version>.exe`**

## Qué hace el script

1. **Maven** – Compila y copia JARs a `target\app`.
2. **JavaFX** – Si no está en el JDK, descarga jmods una vez en `target\fx-jmods`.
3. **jlink** – Arma el runtime con Java + JavaFX en `target\runtime-jre`.
4. **jpackage** – Genera el .exe con acceso directo en menú Inicio y escritorio.

## Icono del instalador

El script usa **`src/main/resources/images/logo-nuevo.ico`** si existe. Si no hay `.ico`, el instalador usa el icono por defecto.

## Actualizar una instalacion existente

Desde la **v3.2.0** el instalador reemplaza solo a la version anterior: no hace falta
desinstalar a mano antes. Al ejecutarlo en una PC que ya tiene el sistema, Windows
desinstala la version vieja e instala la nueva, conservando la base de datos (que vive
en `%LOCALAPPDATA%`, fuera de la carpeta del programa).

Esto lo habilita el `--win-upgrade-uuid` fijo del script: es el identificador que le dice
a Windows que se trata del mismo producto. **Ese GUID no se debe cambiar nunca**; si cambia,
las instalaciones existentes dejan de reconocerse y quedan dos programas duplicados.

Dos aclaraciones importantes:

- **La primera actualizacion no es automatica.** Las instalaciones hechas con la v3.1.0 o
  anteriores se generaron sin ese identificador, asi que el instalador de la v3.2.0 no las
  reconoce. Esa vez todavia hay que desinstalar a mano. De la v3.2.0 en adelante ya funciona solo.
- **Siempre subir la version del `pom.xml`** antes de generar un instalador nuevo. Windows
  decide si reemplaza comparando numeros de version: con la misma version puede rechazar la
  instalacion o no reemplazar nada.

Como las tres ediciones comparten el identificador, instalar la edicion Plus sobre una
Basica tambien reemplaza la instalacion, que es lo deseable para ampliarle la licencia a un cliente.

## Instalar en otra PC

Copiar **`Sistema de Inventario-<version>.exe`** a la otra PC y ejecutarlo. No hace falta instalar Java.  
Los datos se guardan en **`%LOCALAPPDATA%\Sistema de Inventario\data`** (cada usuario de Windows tiene su propia base).
