# Задание 1

В проекте по заданию осталось только:

- аутентификация с JWT access/refresh;
- авторизация по ролям USER/ADMIN;
- HTTPS-конфигурация;
- подключение к PostgreSQL;
- CI pipeline со шагами test и build.

## Что осталось в проекте

- User: учётная запись с логином, паролем и ролью.
- UserSession: запись refresh-сессии со статусами ACTIVE, REFRESHED, REVOKED, EXPIRED.
- AuthController: регистрация, логин, refresh.
- UserController: профиль текущего пользователя и список пользователей для ADMIN.
- SystemController: простые защищённые endpoint-ы для проверки доступа.

## Запуск

Локальный запуск с HTTPS:

```bash
KEYSTORE_PATH="$(pwd)/secrets/tls-1BIB23225/airline_keystore_1BIB23225.p12" \
KEYSTORE_PASSWORD="admin11" \
mvn spring-boot:run
```

Запуск в текущем репозитории

```bash
mvn spring-boot:run
```

Приложение стартует на: `https://localhost:8443`.

## Требования к паролю

- минимум 8 символов;
- минимум одна заглавная буква;
- минимум одна строчная буква;
- минимум одна цифра;
- минимум один спецсимвол.

## Основные endpoint-ы

### Регистрация

```bash
curl -k -X POST "https://localhost:8443/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"User123!","role":"USER"}'
```

### Логин

```bash
TOKENS=$(curl -k -s -X POST "https://localhost:8443/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"User123!"}')

ACCESS=$(echo "$TOKENS" | jq -r '.accessToken')
REFRESH=$(echo "$TOKENS" | jq -r '.refreshToken')
```

### Обновление пары токенов

```bash
curl -k -X POST "https://localhost:8443/api/auth/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH\"}"
```

### Профиль текущего пользователя

```bash
curl -k -H "Authorization: Bearer $ACCESS" \
  "https://localhost:8443/api/users/me"
```

### Проверка защищённого endpoint-а

```bash
curl -k -H "Authorization: Bearer $ACCESS" \
  "https://localhost:8443/api/system/ping"
```

### Endpoint только для ADMIN

```bash
curl -k -H "Authorization: Bearer $ACCESS" \
  "https://localhost:8443/api/system/admin"
```

### Список пользователей для ADMIN

```bash
curl -k -H "Authorization: Bearer $ACCESS" \
  "https://localhost:8443/api/users"
```

## Проверка refresh-ротации

```bash
OLD_REFRESH="$REFRESH"

TOKENS=$(curl -k -s -X POST "https://localhost:8443/api/auth/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH\"}")

REFRESH=$(echo "$TOKENS" | jq -r '.refreshToken')

curl -k -i -X POST "https://localhost:8443/api/auth/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$OLD_REFRESH\"}"
```

Ожидаемо старый refresh перестаёт работать, потому что предыдущая сессия переводится в статус REFRESHED.
