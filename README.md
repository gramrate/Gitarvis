# Gitarvis v2

Голосовой/текстовый ассистент для выполнения простых git-команд через локальную LLM.

## Требования

- Java 21
- Maven
- Git
- Ollama
- Локальная модель Qwen в Ollama

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
  connectTimeoutSeconds: 20
  requestTimeoutSeconds: 60

vosk:
  modelPath: "models/vosk-model-small-ru-0.22"
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
java -cp target/classes src.Main
```

После запуска можно писать git-запросы текстом, например:

```text
покажи статус
покажи последние коммиты
покажи diff
добавь все файлы
сделай коммит с сообщением initial structure
```

Для выхода:

```text
exit
```

## Команды Gitarvis

LLM возвращает JSON с действием, параметрами и текстом ответа. Поддерживаются команды:

- `init` — `git init`
- `add` — `git add .`
- `commit` — `git commit -m "..."`
- `branch_create` — создать ветку, нужен `parameters.name`
- `checkout` — перейти на ветку, нужен `parameters.name`
- `push` — `git push`
- `chat` — обычный разговор без git-действия
- `unknown` — команда не распознана

Если не хватает данных, например сообщения коммита или имени ветки, Gitarvis задаст уточняющий вопрос и выполнит команду после следующего ответа.

## Vosk

Путь к модели Vosk задается в YAML:

```yaml
vosk:
  modelPath: "models/vosk-model-small-ru-0.22"
  nativeLibraryPath: ""
```

Каталог `models/` добавлен в `.gitignore`, потому что модели обычно большие и должны храниться локально.

Сейчас голосовой ввод оставлен как точка расширения в `VoiceInputSource`; для полноценной работы нужно подключить зависимости Vosk и микрофонный ввод.
