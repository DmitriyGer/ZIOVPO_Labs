# Задание 2. Реализация модуля управления лицензиями

## Требуется

1. Реализовать структуру таблиц и связей в PostgreSQL по ER-диаграмме.
2. Реализовать операцию создания лицензии, опираясь на диаграмму последовательности.
3. Реализовать операцию активации лицензии, опираясь на диаграмму последовательности.
4. Реализовать операцию проверки лицензии, опираясь на диаграмму последовательности.
5. Реализовать операцию продления лицензии, опираясь на диаграмму последовательности.
6. Создать класс Ticket для передачи информации о лицензии клиентам. Тикет должен состоять из:
   - Текущей даты сервера
   - Времени жизни тикета
   - Даты активации лицензии
   - Даты истечения лицензии
   - Идентификатора пользователя
   - Идентификатора устройства
   - Флага блокировки лицензии
7. Создать класс TicketResponse, содержащий Ticket и ЭЦП на его основе

(Доп. информация)[https://github.com/MatorinFedor/RBPO_2025_demo/blob/master/files/licenses.md]

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

## Лицензирование

При старте приложения автоматически добавляются:

- продукт `Antivirus` (обычно `id=1`);
- типы лицензий `TRIAL`, `MONTH`, `YEAR` (обычно `id=1..3`).

### Подготовка переменных и тестовых пользователей

```bash
BASE_URL="https://localhost:8443"
TS=$(date +%s)

ADMIN_USERNAME="admin_${TS}"
USER1_USERNAME="owner_${TS}"
USER2_USERNAME="other_${TS}"
PASSWORD_ADMIN="Admin123!"
PASSWORD_USER="User123!"

curl -k -s -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$PASSWORD_ADMIN\",\"role\":\"ADMIN\"}" >/dev/null

curl -k -s -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER1_USERNAME\",\"password\":\"$PASSWORD_USER\",\"role\":\"USER\"}" >/dev/null

curl -k -s -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER2_USERNAME\",\"password\":\"$PASSWORD_USER\",\"role\":\"USER\"}" >/dev/null

ADMIN_ACCESS=$(curl -k -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$PASSWORD_ADMIN\"}" \
  | jq -r '.accessToken')

USER1_ACCESS=$(curl -k -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER1_USERNAME\",\"password\":\"$PASSWORD_USER\"}" \
  | jq -r '.accessToken')

USER2_ACCESS=$(curl -k -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER2_USERNAME\",\"password\":\"$PASSWORD_USER\"}" \
  | jq -r '.accessToken')

USER1_ID=$(curl -k -s -H "Authorization: Bearer $ADMIN_ACCESS" \
  "$BASE_URL/api/users" \
  | jq -r --arg U "$USER1_USERNAME" '.[] | select(.username==$U) | .id')
```

### 1) Проверка структуры таблиц и связей в PostgreSQL

```bash
docker exec postgres-db psql -U admin -d admin_bd \
  -c "select table_name from information_schema.tables where table_schema='public' and table_name in ('users','product','license_type','license','device','device_license','license_history') order by table_name;" \
  -c "select tc.table_name, kcu.column_name, ccu.table_name as foreign_table, ccu.column_name as foreign_column from information_schema.table_constraints tc join information_schema.key_column_usage kcu on tc.constraint_name = kcu.constraint_name and tc.table_schema = kcu.table_schema join information_schema.constraint_column_usage ccu on ccu.constraint_name = tc.constraint_name and ccu.table_schema = tc.table_schema where tc.constraint_type='FOREIGN KEY' and tc.table_schema='public' and tc.table_name in ('device','device_license','license','license_history') order by tc.table_name, kcu.column_name;"
```

### 2) Создание лицензии администратором (успех)

```bash
CREATE_LIMIT=$(curl -k -s -X POST "$BASE_URL/api/licenses" \
  -H "Authorization: Bearer $ADMIN_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"productId\":1,\"typeId\":2,\"ownerId\":$USER1_ID,\"deviceCount\":1,\"description\":\"limit-1\"}")

CODE_LIMIT=$(echo "$CREATE_LIMIT" | jq -r '.code')
echo "$CREATE_LIMIT" | jq
```

### 3) Создание лицензии обычным пользователем (ошибка)

```bash
curl -k -i -X POST "$BASE_URL/api/licenses" \
  -H "Authorization: Bearer $USER1_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"productId\":1,\"typeId\":2,\"ownerId\":$USER1_ID,\"deviceCount\":1}"
```

### 4) Создание лицензии с несуществующим productId (ошибка)

```bash
curl -k -i -X POST "$BASE_URL/api/licenses" \
  -H "Authorization: Bearer $ADMIN_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"productId\":999999,\"typeId\":2,\"ownerId\":$USER1_ID,\"deviceCount\":1}"
```

### 5) Активация лицензии владельцем на первом устройстве (успех)

```bash
ACTIVATE_LIMIT_OK=$(curl -k -s -X POST "$BASE_URL/api/licenses/activate" \
  -H "Authorization: Bearer $USER1_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"activationKey\":\"$CODE_LIMIT\",\"deviceName\":\"Laptop\",\"deviceMac\":\"AA-BB-CC-DD-EE-11\"}")

echo "$ACTIVATE_LIMIT_OK" | jq
```

### 6) Повторная активация на том же устройстве (успех)

```bash
curl -k -X POST "$BASE_URL/api/licenses/activate" \
  -H "Authorization: Bearer $USER1_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"activationKey\":\"$CODE_LIMIT\",\"deviceName\":\"Laptop\",\"deviceMac\":\"AA-BB-CC-DD-EE-11\"}"
```

### 7) Активация вторым устройством при лимите 1 (ошибка)

```bash
curl -k -i -X POST "$BASE_URL/api/licenses/activate" \
  -H "Authorization: Bearer $USER1_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"activationKey\":\"$CODE_LIMIT\",\"deviceName\":\"Phone\",\"deviceMac\":\"AA-BB-CC-DD-EE-12\"}"
```

### 8) Активация лицензии другим пользователем (ошибка)

```bash
curl -k -i -X POST "$BASE_URL/api/licenses/activate" \
  -H "Authorization: Bearer $USER2_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"activationKey\":\"$CODE_LIMIT\",\"deviceName\":\"Other device\",\"deviceMac\":\"AA-BB-CC-DD-EE-99\"}"
```

### 9) Активация с неверным ключом (ошибка)

```bash
curl -k -i -X POST "$BASE_URL/api/licenses/activate" \
  -H "Authorization: Bearer $USER1_ACCESS" \
  -H "Content-Type: application/json" \
  -d '{"activationKey":"BAD-KEY","deviceName":"Laptop","deviceMac":"AA-BB-CC-DD-EE-11"}'
```

### 10) Получение информации о лицензии владельцем (успех)

```bash
CHECK_OK=$(curl -k -s -X POST "$BASE_URL/api/licenses/check" \
  -H "Authorization: Bearer $USER1_ACCESS" \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"deviceMac":"AA-BB-CC-DD-EE-11"}')

echo "$CHECK_OK" | jq
```

### 11) Получение информации о лицензии другим пользователем (ошибка)

```bash
curl -k -i -X POST "$BASE_URL/api/licenses/check" \
  -H "Authorization: Bearer $USER2_ACCESS" \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"deviceMac":"AA-BB-CC-DD-EE-11"}'
```

### 12) Получение информации по несуществующему устройству (ошибка)

```bash
curl -k -i -X POST "$BASE_URL/api/licenses/check" \
  -H "Authorization: Bearer $USER1_ACCESS" \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"deviceMac":"FF-FF-FF-FF-FF-FF"}'
```

### 13) Продление слишком рано (ошибка)

```bash
curl -k -i -X POST "$BASE_URL/api/licenses/renew" \
  -H "Authorization: Bearer $USER1_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"activationKey\":\"$CODE_LIMIT\",\"deviceMac\":\"AA-BB-CC-DD-EE-11\"}"
```

### 14) Успешное продление в допустимом окне (успех)

```bash
CREATE_RENEW=$(curl -k -s -X POST "$BASE_URL/api/licenses" \
  -H "Authorization: Bearer $ADMIN_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"productId\":1,\"typeId\":2,\"ownerId\":$USER1_ID,\"deviceCount\":2,\"description\":\"renew-ok\"}")

CODE_RENEW=$(echo "$CREATE_RENEW" | jq -r '.code')

curl -k -s -X POST "$BASE_URL/api/licenses/activate" \
  -H "Authorization: Bearer $USER1_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"activationKey\":\"$CODE_RENEW\",\"deviceName\":\"RenewDevice\",\"deviceMac\":\"AA-BB-CC-DD-EE-44\"}" >/dev/null

docker exec postgres-db psql -U admin -d admin_bd -c \
  "update license set ending_date = now() + interval '1 day' where code = '$CODE_RENEW';"

curl -k -X POST "$BASE_URL/api/licenses/renew" \
  -H "Authorization: Bearer $USER1_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"activationKey\":\"$CODE_RENEW\",\"deviceMac\":\"AA-BB-CC-DD-EE-44\"}"
```

### 15) Продление другим пользователем (ошибка)

```bash
curl -k -i -X POST "$BASE_URL/api/licenses/renew" \
  -H "Authorization: Bearer $USER2_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"activationKey\":\"$CODE_RENEW\",\"deviceMac\":\"AA-BB-CC-DD-EE-44\"}"
```

### 16) Продление с несуществующим ключом (ошибка)

```bash
curl -k -i -X POST "$BASE_URL/api/licenses/renew" \
  -H "Authorization: Bearer $USER1_ACCESS" \
  -H "Content-Type: application/json" \
  -d '{"activationKey":"NO-SUCH-CODE","deviceMac":"AA-BB-CC-DD-EE-44"}'
```

### 17) Продление с непривязанным устройством (ошибка)

```bash
curl -k -i -X POST "$BASE_URL/api/licenses/renew" \
  -H "Authorization: Bearer $USER1_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"activationKey\":\"$CODE_RENEW\",\"deviceMac\":\"AA-BB-CC-DD-EE-55\"}"
```

### 18) Активация на втором устройстве при лимите 2 (успех)

```bash
CREATE_TWO_DEV=$(curl -k -s -X POST "$BASE_URL/api/licenses" \
  -H "Authorization: Bearer $ADMIN_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"productId\":1,\"typeId\":2,\"ownerId\":$USER1_ID,\"deviceCount\":2,\"description\":\"two-devices\"}")

CODE_TWO_DEV=$(echo "$CREATE_TWO_DEV" | jq -r '.code')

curl -k -s -X POST "$BASE_URL/api/licenses/activate" \
  -H "Authorization: Bearer $USER1_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"activationKey\":\"$CODE_TWO_DEV\",\"deviceName\":\"PC\",\"deviceMac\":\"AA-BB-CC-DD-EE-71\"}" >/dev/null

curl -k -X POST "$BASE_URL/api/licenses/activate" \
  -H "Authorization: Bearer $USER1_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"activationKey\":\"$CODE_TWO_DEV\",\"deviceName\":\"Phone\",\"deviceMac\":\"AA-BB-CC-DD-EE-72\"}"
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
