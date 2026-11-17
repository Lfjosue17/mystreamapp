# Instalación

## Requisitos

- Android Studio (trae el JDK). Git y GitHub Desktop.
- Una clave gratis de TMDB: crea una cuenta en themoviedb.org → foto de perfil → **Ajustes → API** →
  pide una clave (uso personal). Sirve la **API Key** o el **Token de lectura**.

## 1. Bajar el proyecto en otra computadora

1. GitHub Desktop → File → Clone repository → `Lfjosue17/mystreamapp`.
2. Local path: `C:\Users\lfjos\Documents\GitHub\mystreamapp`.
3. Abre la carpeta en Android Studio y espera a que termine “Gradle sync”.

## 2. Archivo `.env` (opcional, nunca se sube)

Copia `.env.example` como `.env` en la raíz del proyecto y llena lo que necesites:

```
TMDB_API_KEY=tu_clave_de_tmdb
FIRMA_ARCHIVO=C:\Users\lfjos\Documents\Llaves\mystreamapp.jks
FIRMA_CLAVE_ALMACEN=...
FIRMA_ALIAS=...
FIRMA_CLAVE_LLAVE=...
```

- Sin `TMDB_API_KEY`, la clave se puede pegar dentro de la app (Ajustes → Claves).
- Sin los datos de firma, la versión release se compila sin firmar.

## 3. Compilar

En PowerShell, dentro de la carpeta del proyecto:

```powershell
.\gradlew.bat assembleDebug
```

El APK queda en `app\build\outputs\apk\debug\app-debug.apk`.

Versión firmada (necesita el `.env` con la firma):

```powershell
.\gradlew.bat assembleRelease
```

Queda en `app\build\outputs\apk\release\app-release.apk`.

### Crear la llave de firma (solo una vez)

Android Studio → Build → Generate Signed App Bundle or APK → APK → **Create new…** y guárdala en
`C:\Users\lfjos\Documents\Llaves\mystreamapp.jks`. Pon esos datos en `.env`.
Guarda bien la llave: si se pierde, la app ya no se puede actualizar encima (hay que desinstalar).

## 4. Instalar sin cable USB

- **Teléfono:** manda el APK por Google Drive, correo o WhatsApp y ábrelo en el teléfono.
  La primera vez pide permitir “Instalar apps desconocidas” para esa app.
- **Android TV / Google TV:** usa la app *Send Files to TV* o *Downloader* para pasar el APK, y ábrelo
  con un administrador de archivos.

## 5. Probar la interfaz en la PC (opcional)

La interfaz está en `app\src\main\assets\www`. Se puede ver en el navegador con un servidor local:

```powershell
npx serve app\src\main\assets\www
```

En el navegador no existe el reproductor nativo: los canales se abren en otra pestaña y algunas
listas pueden fallar por CORS. En el teléfono todo funciona por el lado nativo.

## Problemas comunes

- **`gradlew` no se reconoce:** escribe `.\gradlew.bat`, con el punto y la barra.
- **“SDK location not found”:** abre el proyecto una vez en Android Studio para que cree `local.properties`.
- **La app dice “Falta la clave de TMDB”:** ponla en `.env` y recompila, o pégala en Ajustes → Claves.
