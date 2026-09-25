# Публикация Shader Gallery: CI/CD и база данных

Актуально для репозитория `RenjerStats/shader-gallery` и проекта Supabase `opjadrnmcghpwxrsryus`. Публичный сайт: <https://renjerstats.github.io/shader-gallery/>. Все команды ниже выполняются **из корня репозитория**, если не сказано иначе.

## Что публикуется автоматически

При каждом `push` в ветку `main` GitHub запускает [`.github/workflows/pages.yml`](../.github/workflows/pages.yml):

1. Устанавливает Node.js 22 и зависимости через `npm ci`.
2. Проверяет TypeScript и собирает Vite-сайт командой `npm run build` с `GITHUB_PAGES=true`.
3. Подставляет в сборку публичные URL и publishable key Supabase из workflow.
4. Копирует `build/index.html` в `build/404.html`, чтобы прямые ссылки на страницы работали на GitHub Pages.
5. Публикует `build/` через GitHub Pages.

Результат и ошибки смотрите в [Actions → Publish Shader Gallery](https://github.com/RenjerStats/shader-gallery/actions/workflows/pages.yml). Зелёный запуск означает успешную публикацию сайта. В `Settings → Pages` источником должен оставаться **GitHub Actions**. Workflow можно запустить вручную кнопкой **Run workflow** на той же странице.

**Важная граница:** этот workflow не запускает `npm test`, не применяет миграции Supabase, не публикует Edge Function и не собирает Android APK. Успешная сборка сайта сама по себе не обновляет базу и серверный API.

## Карта изменений

| Что изменили | Где исходники | Как выпустить |
| --- | --- | --- |
| Интерфейс сайта | `apps/web/`, `apps/web/public/` | `push` в `main`; дождаться успешного Pages workflow |
| Схема БД, индексы, триггеры, RLS | `supabase/migrations/` | Создать новую миграцию и применить `npx supabase db push` |
| Облачный API | `supabase/functions/gallery/`, `supabase/functions/_shared/` | `npx supabase functions deploy gallery --no-verify-jwt` |
| Android | `apps/android/` | Собрать и установить новый APK вручную; GitHub Pages его не обновляет |
| Локальный сервер разработки | `server/`, `data/pglite/` | Запустить локально; он не обслуживает публичный сайт |

Публичный сайт использует Supabase Auth, базу и Edge Function `gallery`. Android по умолчанию подключается к этому же проекту. Локальный сервер `npm run dev` использует отдельную PGlite-базу: данные в `data/pglite/` не появляются автоматически в Supabase.

## Подготовка нового компьютера

Нужны Git, Node.js 22+, npm и доступ к GitHub и Supabase с правом выпуска. В корне проекта:

```powershell
npm ci
npx supabase login
npx supabase link --project-ref opjadrnmcghpwxrsryus
```

Команда `link` может запросить пароль базы Supabase. Его берут в панели проекта и вводят в приглашение CLI; не добавляйте пароль, `service_role` key или access token в файлы репозитория и сообщения. Привязка и авторизация CLI являются настройкой данного компьютера, а `supabase/config.toml` хранит только ID проекта и настройку функции.

Для Android нужны Android SDK 35, JDK 17 и Gradle 8.13 либо Android Studio с этими компонентами. В репозитории пока нет Gradle Wrapper: команда `gradle` должна быть доступна на компьютере.

## Обычный выпуск сайта

```powershell
npm ci
npm test
npm run build
git status
git add .
git diff --cached --stat
git commit -m "Описание изменения"
git push origin main
```

Перед `commit` проверьте список подготовленных файлов и убедитесь, что в нём нет локальных данных или секретов. После `push` откройте запуск в [GitHub Actions](https://github.com/RenjerStats/shader-gallery/actions/workflows/pages.yml), дождитесь успешного шага `deploy` и проверьте нужный экран на [публичном сайте](https://renjerstats.github.io/shader-gallery/). `npm test` выполняется здесь локально: в текущем Pages workflow тесты не включены. Если сборка упала, откройте упавший шаг в Actions, исправьте исходники и сделайте новый `push`.

Сборка Pages получает `VITE_SUPABASE_URL` и `VITE_SUPABASE_PUBLISHABLE_KEY` из [workflow](../.github/workflows/pages.yml). Publishable key предназначен для клиента и не заменяет проверку прав. Секреты, пароль БД и `service_role` key в Vite-переменные `VITE_*` не помещать: они попадут в браузерную сборку.

## Изменение базы данных

Не редактируйте уже применённые SQL-файлы задним числом. Добавьте новую миграцию:

```powershell
npx supabase migration new имя_изменения
```

Команда создаст `supabase/migrations/<timestamp>_имя_изменения.sql`. Запишите SQL туда, проверьте воздействие на существующие данные и права доступа. Для таблиц, доступных клиентам, проверьте RLS; в нынешней архитектуре клиенты обращаются к таблицам через Edge Function, а прямые права `anon`/`authenticated` ограничены. Перед удалением или необратимым преобразованием данных сделайте отдельный проверенный экспорт БД.

Убедитесь, что CLI связан **именно** с `opjadrnmcghpwxrsryus`, затем посмотрите список ожидающих миграций и примените их:

```powershell
npx supabase migration list
npx supabase db push --dry-run
npx supabase db push
```

`db push` применяет ожидающие миграции к удалённой БД и записывает их в историю; повторный запуск уже применённые миграции пропускает. После выпуска проверьте затронутый сценарий в публичном приложении и [Database → Migrations](https://supabase.com/dashboard/project/opjadrnmcghpwxrsryus/database/migrations) либо повторно выполните `npx supabase migration list`. Не используйте `db reset` для облачного проекта: это команда для локальной тестовой БД.

Изменения, сделанные вручную в SQL Editor, не создают миграцию в репозитории. Для воспроизводимости переносите их в новую миграцию; не считайте редактирование SQL в панели полноценным выпуском.

## Изменение Edge Function

Если менялся `supabase/functions/gallery/index.ts` или общий код в `supabase/functions/_shared/`, после проверок выпустите функцию отдельно:

```powershell
npm test
npx supabase functions deploy gallery --no-verify-jwt
```

Проверьте результат в [Edge Functions → gallery](https://supabase.com/dashboard/project/opjadrnmcghpwxrsryus/functions) и выполните затронутый сценарий на сайте или телефоне. Параметр `--no-verify-jwt` соответствует текущей настройке `verify_jwt = false` в [`supabase/config.toml`](../supabase/config.toml): функция допускает публичное чтение и сама проверяет токен для действий пользователя. Изменять этот режим нужно вместе с кодом авторизации и проверкой гостевых сценариев.

Облачная функция использует настройки окружения Supabase для URL проекта, publishable key и подключения к БД. Если после выпуска возникает ошибка конфигурации, смотрите логи функции и её Secrets в панели Supabase; не подставляйте секреты в клиентский код.

## Изменение нескольких частей сразу

Если новому сайту или Android-клиенту нужны новая структура БД и новый API, порядок выпуска такой:

1. Создайте миграцию и код функции совместимыми со старым клиентом, выполните `npm test` и `npm run build`.
2. Примените миграцию через `db push`; проверьте результат.
3. Опубликуйте Edge Function; проверьте старый сценарий и новый API.
4. Отправьте клиентские изменения в `main`, дождитесь Pages и проверьте сайт.
5. Соберите и установите обновлённый Android APK.

Не удаляйте используемые поля и ответы API в том же выпуске, в котором меняется клиент: ранее открытая вкладка и установленные APK могут продолжать обращаться к старому контракту. Сначала добавьте новый вариант, затем обновите клиентов и лишь отдельным изменением уберите старый.

## Android APK

Из каталога `apps/android/` в PowerShell:

```powershell
$env:JAVA_HOME='C:\path\to\jdk-17'
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
gradle :app:assembleDebug
```

Debug APK: `apps/android/app/build/outputs/apk/debug/app-debug.apk`. Его можно установить из Android Studio или с установленным `adb`:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

APK не публикуется через Pages и не рассылается пользователям автоматически. При распространении новой версии отдельно продумайте подписанную release-сборку и способ доставки. При смене адреса сайта/Supabase проверьте значения в `GalleryClient.kt`, OAuth callback в `AndroidManifest.xml`, разрешённые redirect URLs в Supabase Auth и CORS в Edge Function.

## Быстрая проверка после выпуска

- Сайт открывается по HTTPS, страницы работ открываются по прямой ссылке.
- Вход по Google и по почте доступен; новая регистрация по почте показывает требование подтвердить адрес.
- Галерея загружается без ошибки API. Если есть опубликованная работа, откройте её и проверьте действие, которое меняли.
- Android показывает ту же облачную галерею; для изменения авторизации проверьте соответствующий вход на телефоне.

Если сайт обновился, а API ведёт себя по-старому, проверьте отдельный выпуск Edge Function. Если API ожидает отсутствующее поле, проверьте историю миграций. Если Pages не обновился, проверьте запуск Actions и при необходимости повторите его через **Re-run jobs** после устранения причины сбоя.

## Если выпуск нужно откатить

- **Сайт:** отмените проблемный коммит отдельным `git revert <hash>` и отправьте результат в `main`; Pages опубликует предыдущий код заново. Учитывайте, что изменения БД и функции при этом останутся действующими.
- **Edge Function:** восстановите нужную версию её исходников в новом коммите и повторите `npx supabase functions deploy gallery --no-verify-jwt`.
- **База:** уже выполненную миграцию не удаляйте и не переписывайте. Для исправления создайте следующую миграцию; если данные были удалены, восстановление возможно только из заранее сделанного экспорта или доступной резервной копии.
- **Android:** установите исправленный APK; отмена коммита на GitHub не изменит уже установленные приложения.

Справка: [Supabase CLI и миграции](https://supabase.com/docs/reference/cli/supabase-db-push), [выпуск Edge Functions](https://supabase.com/docs/guides/functions/deploy), [публикация GitHub Pages через Actions](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site).
