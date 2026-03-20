# Generar el instalador .exe

Resumen rápido. Para requisitos y estructura del proyecto ver [README.md](README.md).

## Requisitos

- **JDK 17 o 21** con `JAVA_HOME` configurado.
- **Maven** en el PATH (o `MAVEN_HOME`).
- **Conexión a internet** la primera vez (descarga de JavaFX jmods).

## Pasos

1. Abrir CMD en la carpeta del proyecto.
2. Ejecutar: **`build-installer.bat`**  
   (o **`Ejecutar-crear-instalador.bat`** para que la ventana no se cierre).
3. Al terminar, el instalador queda en:  
   **`target\installer\Sistema de Inventario-1.0.0.exe`**

## Qué hace el script

1. **Maven** – Compila y copia JARs a `target\app`.
2. **JavaFX** – Si no está en el JDK, descarga jmods una vez en `target\fx-jmods`.
3. **jlink** – Arma el runtime con Java + JavaFX en `target\runtime-jre`.
4. **jpackage** – Genera el .exe con acceso directo en menú Inicio y escritorio.

## Icono del instalador

El script usa **`src/main/resources/images/logo-nuevo.ico`** si existe. Si no hay `.ico`, el instalador usa el icono por defecto.

## Instalar en otra PC

Copiar **`Sistema de Inventario-1.0.0.exe`** a la otra PC y ejecutarlo. No hace falta instalar Java.  
Los datos se guardan en **`%LOCALAPPDATA%\Sistema de Inventario\data`** (cada usuario de Windows tiene su propia base).
