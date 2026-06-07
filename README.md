# Gitarvis v2

Голосовой ассистент для выполнения простых git-команд через локальную LLM.

## Требования

- Java 21
- Maven
- Git
- Ollama
- Локальная модель Qwen в Ollama
- Русская модель Vosk в каталоге `models/vosk-model-small-ru`

## Подготовка Ollama

Проверь, что Ollama запущена:

```bash
ollama serve
```

В отдельном терминале проверь список моделей:

```bash
ollama list
```

Если нужной модели нет, скачай ее. Например:

```bash
ollama pull qwen2.5:3b
```

Имя модели в конфиге должно точно совпадать с именем из `ollama list`.

## Конфиг

Основной конфиг лежит здесь:

```text
src/main/resources/application.yml
```

Текущие настройки для Ollama:

```yaml
ai:
  baseUrl: "http://localhost:11434/v1"
  apiKey: ""
  model: "qwen2.5:3b"
  temperature: 0.1
  maxTokens: 160
  connectTimeoutSeconds: 20
  requestTimeoutSeconds: 120

vosk:
  modelPath: "models/vosk-model-small-ru"
  nativeLibraryPath: ""
```

Если твоя модель называется иначе, поменяй:

```yaml
model: "имя-модели-из-ollama-list"
```

Можно создать локальный `config.yml` в корне проекта. Он не попадет в git и будет использован вместо `application.yml`.

Также путь к конфигу можно передать явно:

```bash
java -Dgitarvis.config=/path/to/config.yml -cp target/classes src.Main
```

## Сборка

```bash
mvn test
```

или:

```bash
mvn -q verify
```

## Запуск

Сначала собери проект:

```bash
mvn -q -DskipTests package
```

Потом запусти:

```bash
java -jar target/Gitarvisv2-1.0-SNAPSHOT.jar
```

После запуска ассистент пишет `Я в режиме ожидания.` Обычные команды в этом режиме не выполняются.

Чтобы начать работу, позови его по имени. Пробуждение в режиме ожидания строго локальное, без AI-проверки.

Основное имя: `Матвей`. Также сработают производные и варианты распознавания Vosk: `матвейка`, `мат`, `мэт`, `мет`, а также слова, которые начинаются на `матве`, `мотве`, `мэтве`, `метве`.

```text
Матвей
Матвейка
Мэт покажи статус
```

В активном режиме говори git-запросы голосом, например:

```text
список команд
покажи статус
покажи последние коммиты
покажи diff
добавь все файлы
сделай коммит с сообщением initial structure
добавь и сохрани с сообщением Обновил все поля
```

Для выхода:

```text
завершение работы
закрой программу
exit
```

Чтобы вернуть ассистента в режим ожидания без закрытия программы:

```text
спасибо
стоп
пока
выключись
```

## Команды Gitarvis

LLM возвращает JSON с действием, параметрами и текстом ответа. Поддерживаются команды:

- `init` — `git init`
- `status` — `git status`
- `add` — `git add .`
- `commit` — `git commit -m "..."`
- `add_commit` — `git add .`, затем `git commit -m "..."`
- `branch_create` — создать ветку, нужен `parameters.name`
- `checkout` — перейти на ветку, нужен `parameters.name`
- `push` — `git push origin <текущая-ветка>`
- `help` — показать список команд
- `chat` — обычный разговор без git-действия
- `unknown` — команда не распознана

Если не хватает данных, например сообщения коммита или имени ветки, Gitarvis задаст уточняющий вопрос и выполнит команду после следующего ответа.

## Vosk

Путь к модели Vosk задается в YAML:

```yaml
vosk:
  modelPath: "models/vosk-model-small-ru"
  nativeLibraryPath: ""
```

Каталог `models/` добавлен в `.gitignore`, потому что модели обычно большие и должны храниться локально.

Если нативная библиотека Vosk не подхватилась автоматически, укажи путь в `vosk.nativeLibraryPath`.
