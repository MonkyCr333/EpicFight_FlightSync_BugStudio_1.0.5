# EpicFight Flight Sync - BugStudio

Parche de compatibilidad para Epic Fight que sincroniza el vuelo de jugadores remotos.

Evita que otros jugadores se vean caminando por el aire cuando están volando con creativo, `/fly`, plugins o tags.

## ¿Qué hace?

Este mod corrige un problema visual de Epic Fight donde un jugador remoto puede verse caminando por el aire en lugar de usar una animación de vuelo.

El servidor detecta cuando un jugador está volando y sincroniza ese estado con los clientes cercanos para que Epic Fight pueda mostrar la animación correcta.

## Características

* Sincronización server -> client del estado de vuelo.
* Soporte para vuelo vanilla, creativo, `/fly`, plugins y scoreboard tags.
* Config sincronizada desde el servidor.
* Comando `/efsync reload`.
* ACK cliente-servidor para confirmar recepción de paquetes.
* Modo `ABILITIES`.
* Fallback opcional con `HYBRID` y `MOTION_OVERRIDE`.
* Compatible con Forge/Mohist 1.20.1.

## Requisitos

* Minecraft 1.20.1
* Forge 47.x
* Java 17
* Epic Fight 20.14.17

## Dependencias locales

Para compilar, coloca estos archivos dentro de la carpeta `libs/`:

```txt
epic-fight-20.14.17-mc1.20.1-forge.jar
epic-fight-invincible-lib-20.14.8.2-mc1.20.1-forge.jar
```

Estos `.jar` no están incluidos en el repositorio.

## Compilar

Desde la raíz del proyecto:

```powershell
.\gradlew clean reobfJar
```

El `.jar` final se genera en:

```txt
build/libs/
```

## Configuración

El archivo de configuración se genera en:

```txt
config/epicfight_flight_sync.properties
```

Opciones principales:

```properties
debug=false
syncServerConfigToClients=true

detectVanillaFlying=true
detectScoreboardTag=true
flightTag=epicfight_flying

remoteFlightMode=ABILITIES
forceRemoteFlightMotion=false

directSyncAllDimensions=true
resendTrueFlightState=true
trueStateResendCount=3
```

## Comando

```mcfunction
/efsync reload
```

Recarga la configuración del servidor y la sincroniza con los clientes conectados.

## Autor

BugStudio

## Licencia

MIT
s://docs.minecraftforge.net/en/1.20.1/gettingstarted/
LexManos' Install Video: https://youtu.be/8VEdtQLuLO0
Forge Forums: https://forums.minecraftforge.net/
Forge Discord: https://discord.minecraftforge.net/
