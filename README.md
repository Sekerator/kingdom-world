# KingdomWorld

[Русский](#русский) | [English](#english)

## Русский

KingdomWorld: мод для Minecraft 1.20.1 (Forge) о королевствах, которые развиваются по всему миру. В планах: экономика, налоги и своя валюта, жители с профессиями и производственными цепочками, армия, знать, фракции, дипломатия, войны и интриги. Любой игрок может основать своё королевство. Кроме того: гильдия авантюристов с рангами F-SSS и кристаллами с монстров (энергия королевства), башня магов, карта королевств и границ.

### Состояние

Этап 0: каркас проекта. Игровых механик пока нет. Есть модель королевства и её хранение в мире, сетевой канал с проверкой запросов на сервере, конфиги, отладочные команды, тесты и smoke-проверка на настоящем сервере.

### Требования

- Minecraft 1.20.1
- Forge 47.4.10 или новее в ветке 47.x
- Java 17
- Мод ставится и на сервер, и на клиент. Версии должны совпадать, иначе подключение отклоняется.
- Других обязательных модов нет. Совместимость с другими модами (Create и т.д.) включается сама, только если такой мод установлен.

Мод рассчитан на выделенный сервер: вся логика и все изменения состояния выполняются только на сервере, клиент лишь показывает данные и отправляет запросы, которые сервер проверяет.

### Сборка

```
gradlew build
```

Готовый jar: `build/libs/kingdomworld-<версия>.jar`. Нужен JDK 17: если системная `java` другой версии, задайте `JAVA_HOME`, например:

```
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot
gradlew build
```

Первая сборка скачивает и подготавливает Minecraft, это занимает несколько минут.

### Тесты

| Что | Команда |
|---|---|
| Юнит-тесты ядра и архитектурные проверки | `gradlew test` (входит в `gradlew build`) |
| Игровые тесты Forge GameTest | `gradlew runGameTestServer` |
| Smoke-тест на чистом выделенном сервере | `powershell -ExecutionPolicy Bypass -File scripts/smoke-server.ps1` |
| Smoke-тест с модами сборки | `powershell -ExecutionPolicy Bypass -File scripts/smoke-server.ps1 -Pack` |

Архитектурные тесты проверяют, что пакет `core` не зависит от Minecraft, Forge и Mojang, что клиентский код вызывается только через защищённые точки входа и что модули совместимости не используются напрямую.

`runGameTestServer` запускает игровые тесты в отдельном мире (`run-gametest/`) и завершается с ненулевым кодом, если хотя бы один тест упал.

Smoke-скрипт ставит настоящий сервер Forge 1.20.1-47.4.10 в `D:\Projects\kingdom-world-testenv` (вне репозитория), кладёт туда собранный jar, принимает EULA и дважды запускает сервер на свежем мире: в первый раз создаёт королевство `SmokeTest`, во второй проверяет, что оно сохранилось. Скрипт завершается с кодом 1, если сервер не запустился или не остановился, если в логах есть ошибки или исключения, связанные с `kingdomworld`, если на сервере загрузился клиентский класс мода или если данные не пережили перезапуск. Логи каждого запуска лежат в `smoke-logs` внутри папки сервера.

Параметры скрипта:

- `-Pack`: сервер в `server-pack` с серверными и общими модами из папки сборки (по умолчанию `D:\Games\Minecraft\Instances\Taghi RPG & Create RU\mods`; папка только читается). Чисто клиентские моды пропускаются.
- `-SkipBuild`: взять уже собранный jar.
- `-WithoutMod`: диагностический запуск без KingdomWorld, чтобы понять, не роняет ли сервер какой-то мод сборки сам по себе.
- `-ExcludeModIds a,b`: дополнительно исключить моды по id.
- `-TestEnvRoot`, `-PackModsDir`, `-JavaHome`, `-Memory`: пути и память сервера.

### Запуск в среде разработки

- `gradlew runClient`: клиент с модом (папка `run/`).
- `gradlew runServer`: выделенный сервер с модом (папка `run/`, EULA нужно принять в `run/eula.txt`).

### Команды

Уровень прав 2 (оператор):

- `/kingdomworld debug create <название>`: основать королевство (правителем становится вызвавший игрок).
- `/kingdomworld debug list`: список королевств.
- `/kingdomworld debug info <название>`: сведения о королевстве.
- `/kingdomworld debug remove <название>`: удалить королевство.

### Конфиги

- `<мир>/serverconfig/kingdomworld-server.toml` (на мир): `maxActiveNpcs`, `simulationIntervalTicks`, `afkTimeoutSeconds`, `combatTagSeconds`. Пока это заготовки для следующих этапов.
- `config/kingdomworld-common.toml`: `disabledCompatModules`, список id модов, модули совместимости с которыми не включать.
- `config/kingdomworld-client.toml`: `requestKingdomListOnJoin`, запрашивать ли список королевств при входе в мир.

### Структура кода

Пакет `io.github.sekerator.kingdomworld`:

- `core`: чистая Java-логика без Minecraft (модель королевств, правила проверки запросов). Быстро тестируется юнит-тестами.
- `common`: всё, что работает на обеих сторонах: регистрации, хранение (`SavedData` с версией формата и миграциями), сеть, команды, конфиг, серверный API, игровые тесты.
- `client`: только клиентский код. Подключается лишь на клиенте, выделенный сервер эти классы не загружает.
- `compat`: мягкая совместимость с другими модами. Модуль включается только если мод установлен.

Данные королевств хранятся в `<мир>/data/kingdomworld_kingdoms.dat`.

### Лицензия

All Rights Reserved.

## English

KingdomWorld is a Minecraft 1.20.1 (Forge) mod about kingdoms that grow across the world. Planned: economy, taxes and a currency, villagers with professions and production chains, armies, nobility, factions, diplomacy, wars and intrigue. Any player can found a kingdom. Also: an adventurers guild with ranks F-SSS and monster crystals (kingdom energy), a mage tower and a map of kingdoms and borders.

### Status

Stage 0: project skeleton. There are no gameplay mechanics yet. It has the kingdom model and its world storage, a network channel with server-side request validation, configs, debug commands, tests and a smoke test on a real server.

### Requirements

- Minecraft 1.20.1
- Forge 47.4.10 or newer in the 47.x line
- Java 17
- Install the mod on both the server and the client. Versions must match, otherwise the connection is refused.
- No other mods are required. Integrations with other mods (Create and so on) switch on automatically, only when that mod is installed.

The mod is built for dedicated servers: all logic and every state change run on the server only; the client only displays data and sends requests that the server validates.

### Building

```
gradlew build
```

The jar ends up in `build/libs/kingdomworld-<version>.jar`. JDK 17 is required; if the system `java` is a different version, set `JAVA_HOME` first (see the Russian section for an example). The first build downloads and prepares Minecraft, which takes a few minutes.

### Tests

| What | Command |
|---|---|
| Core unit tests and architecture checks | `gradlew test` (part of `gradlew build`) |
| Forge GameTests | `gradlew runGameTestServer` |
| Smoke test on a clean dedicated server | `powershell -ExecutionPolicy Bypass -File scripts/smoke-server.ps1` |
| Smoke test with the modpack's mods | `powershell -ExecutionPolicy Bypass -File scripts/smoke-server.ps1 -Pack` |

The architecture tests check that the `core` package does not depend on Minecraft, Forge or Mojang, that client code is only reached through guarded entry points and that compat modules are not referenced directly.

`runGameTestServer` runs the game tests in a separate world (`run-gametest/`) and exits with a non-zero code if any test fails.

The smoke script installs a real Forge 1.20.1-47.4.10 server into `D:\Projects\kingdom-world-testenv` (outside the repository), adds the built jar, accepts the EULA and starts the server twice on a fresh world: the first session creates the kingdom `SmokeTest`, the second checks that it was saved. The script exits with code 1 if the server fails to start or stop, if the logs contain errors or exceptions related to `kingdomworld`, if a client class of the mod is loaded on the server, or if the data does not survive the restart. Logs of each session are kept in `smoke-logs` inside the server folder.

Script options:

- `-Pack`: a server in `server-pack` with the server-side and common mods of the modpack (default `D:\Games\Minecraft\Instances\Taghi RPG & Create RU\mods`; the folder is only read). Client-only mods are skipped.
- `-SkipBuild`: use the jar that is already built.
- `-WithoutMod`: diagnostic run without KingdomWorld, to see whether a pack mod breaks the server on its own.
- `-ExcludeModIds a,b`: leave out additional mods by id.
- `-TestEnvRoot`, `-PackModsDir`, `-JavaHome`, `-Memory`: paths and server memory.

### Running in development

- `gradlew runClient`: client with the mod (folder `run/`).
- `gradlew runServer`: dedicated server with the mod (folder `run/`; accept the EULA in `run/eula.txt`).

### Commands

Permission level 2 (operator):

- `/kingdomworld debug create <name>`: found a kingdom (the executing player becomes its ruler).
- `/kingdomworld debug list`: list kingdoms.
- `/kingdomworld debug info <name>`: show a kingdom.
- `/kingdomworld debug remove <name>`: remove a kingdom.

### Configuration

- `<world>/serverconfig/kingdomworld-server.toml` (per world): `maxActiveNpcs`, `simulationIntervalTicks`, `afkTimeoutSeconds`, `combatTagSeconds`. These are placeholders for later stages.
- `config/kingdomworld-common.toml`: `disabledCompatModules`, mod ids whose compat modules stay off.
- `config/kingdomworld-client.toml`: `requestKingdomListOnJoin`, whether to request the kingdom list after joining a world.

### Code layout

Package `io.github.sekerator.kingdomworld`:

- `core`: plain Java domain logic without Minecraft (kingdom model, request validation rules). Fast to unit test.
- `common`: everything that runs on both sides: registries, persistence (`SavedData` with a format version and migrations), networking, commands, config, the server API and game tests.
- `client`: client-only code. Wired in on the client only; a dedicated server never loads these classes.
- `compat`: soft integrations with other mods. A module is activated only when its mod is installed.

Kingdom data is stored in `<world>/data/kingdomworld_kingdoms.dat`.

### License

All Rights Reserved.
