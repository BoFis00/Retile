# Retile

A creative-mode building brush for Minecraft **1.21.11** (Fabric). Hold a wooden axe and paint a sphere or cylinder of blocks, where every block is picked from a weighted palette you define.

Works on both client and server. The server validates and applies every stroke, so it's safe on multiplayer creative servers.

## Features

- **Weighted palette:** up to 64 blocks, weight 1–100 each, deterministic per seed
- **Sphere or cylinder:** radius 1–32, with an optional world-Y range
- **Chaos:** `0` picks every block independently, `1` keeps one pick per Y layer, `-1` keeps one pick per X/Z column, values in between blend both
- **Live outlines:** shows exactly which blocks the next apply will change
- **Drag strokes:** hold the mouse and sweep to paint, one stroke = one undo step
- **Undo / redo:** per-player, up to 30 steps or 500 000 blocks, configurable hotkeys
- **Presets:** up to 32 saved palettes
- **Filters:** restrict which blocks can be replaced, optionally including air

## Usage

1. Hold a **wooden axe** in creative mode.
2. **Right-click** to open the settings screen.
3. On **"Replace with"** add blocks and set their weights.
4. On **"Replace these"** optionally set a filter of blocks that may be changed.
5. Press **Esc** to save and close.
7. **Left-click** to apply once, or **hold and drag** to paint a stroke.

## Limits

- Creative-only
- Blocks with block entities and fluids are not supported
- Blocks are placed in their default state
- One stroke is capped at 500 000 changed blocks
- Unloaded chunks are never force-loaded
- The server validates every packet (creative mode, axe in hand, block IDs, radius, weights, distance up to 80 blocks)

## Building

Requires Java 21.

```
./gradlew build
```

The jar appears in `build/libs/`.

## License

[CC0-1.0](LICENSE): public domain.

<details>
<summary>🇷🇺 Русская версия</summary>

# Retile

Строительная кисть для творческого режима Minecraft **1.21.11** (Fabric). Держишь деревянный топор и рисуешь сферу или цилиндр из блоков, где каждый блок выбирается из твоей палитры с весами.

Работает на клиенте и сервере. Сервер проверяет и применяет каждый мазок, так что на мультиплеере это безопасно.

## Возможности

- **Палитра с весами:** до 64 блоков, вес 1–100, детерминированный выбор по сиду
- **Сфера или цилиндр:** радиус 1–32, плюс необязательный диапазон по Y
- **Хаос:** `0` выбирает каждый блок независимо, `1` один выбор на слой по Y, `-1` один выбор на колонну X/Z, промежуточные значения смешивают оба режима
- **Живой контур:** показывает, какие блоки изменит следующее применение
- **Протяжка:** зажми кнопку мыши и води курсором, один мазок = один шаг отмены
- **Отмена / повтор:** для каждого игрока, до 30 шагов или 500 000 блоков, свои горячие клавиши
- **Пресеты:** до 32 сохранённых палитр
- **Фильтры:** какие блоки можно заменять, при желании включая воздух

## Как пользоваться

1. Возьми **деревянный топор** в творческом режиме.
2. **ПКМ**, чтобы открыть настройки.
3. На вкладке **"Replace with"** добавь блоки и задай веса.
4. На вкладке **"Replace these"** при желании задай фильтр заменяемых блоков.
5. **Esc** сохраняет и закрывает настройки.
6. **ЛКМ** применяет один раз, **зажатие и протяжка** рисуют мазок.

## Ограничения

- Только творческий режим
- Блоки с блок-сущностями и жидкости не поддерживаются
- Блоки ставятся в состоянии по умолчанию
- Лимит одного мазка 500 000 блоков
- Незагруженные чанки не подгружаются
- Сервер проверяет каждый пакет (режим, топор в руке, ID блоков, радиус, веса, расстояние до 80 блоков)

## Сборка

Нужна Java 21.

```
./gradlew build
```

Готовый jar появится в `build/libs/`.

## Лицензия

[CC0-1.0](LICENSE): общественное достояние.

</details>
