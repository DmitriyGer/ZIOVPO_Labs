# Задание 4. Реализация модуля управления антивирусными сигнатурами

## Требуется

1. Реализовать структуру таблиц и связей в PostgreSQL согласно требованиям.
2. Реализовать все требуемые операции для работы с сигнатурами.
3. Подключить модуль ЭЦП для формирования подписи сигнатур.
4. Протестируйте работу всех операций и убедитесь, что:
   - реализованы все 8 операций;
   - create/update действительно пересчитывают подпись;
   - delete является логическим;
   - update и delete пишут запись в History;
   - create/update/delete пишут запись в Audit;
   - полная выгрузка не содержит DELETED;
   - инкремент содержит все записи с updatedAt > since, включая DELETED;
   - историю и аудит можно получить по signatureId.

[Методическое пособие по созданию API для управления сигнатурами](https://github.com/MatorinFedor/RBPO_2025_demo/blob/master/files/malware_signatures.md)

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

## ЭЦП

Локально использует модуль ЭЦП `src/main/resources/signature/signing.jks`.

Для GitHub Actions добавляем:

- Secret `SIGNATURE_KEYSTORE_B64`
- Secret `SIGNATURE_KEYSTORE_PASSWORD`
- Secret `SIGNATURE_KEY_PASSWORD`
- Variable `SIGNATURE_KEY_ALIAS`
- Variable `SIGNATURE_PUBLIC_KEY_BASE64`

Сформировать значения можно так:

```bash
SIGNATURE_KEYSTORE_B64=$(base64 < src/main/resources/signature/signing.jks | tr -d '\n')

SIGNATURE_PUBLIC_KEY_BASE64=$(keytool -exportcert -rfc \
  -alias ticket-signing \
  -keystore src/main/resources/signature/signing.jks \
  -storepass admin11 \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | base64 | tr -d '\n')
```

## В API добавлены

Методы:

- `POST /api/licenses/activate`
- `POST /api/licenses/check`
- `POST /api/licenses/renew`

теперь возвращают `TicketResponse`:

```json
{
  "ticket": {
    "serverDate": "2026-04-04T12:14:13.29591+03:00",
    "ticketTtlSeconds": 300,
    "activationDate": "2026-04-04T09:14:13.262073Z",
    "expirationDate": "2026-05-04T09:14:13.262073Z",
    "userId": 25,
    "deviceId": 8,
    "blocked": false
  },
  "signature": "Base64..."
}
```

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
MAC_SEED=$(printf '%04X' $((TS % 65535)))
DEVICE1_MAC="AA-BB-${MAC_SEED:0:2}-${MAC_SEED:2:2}-EE-11"
DEVICE2_MAC="AA-BB-${MAC_SEED:0:2}-${MAC_SEED:2:2}-EE-12"
OTHER_DEVICE_MAC="AA-BB-${MAC_SEED:0:2}-${MAC_SEED:2:2}-EE-99"
MISSING_DEVICE_MAC="FF-FF-${MAC_SEED:0:2}-${MAC_SEED:2:2}-EE-FF"
RENEW_DEVICE_MAC="AA-BB-${MAC_SEED:0:2}-${MAC_SEED:2:2}-EE-44"
UNBOUND_DEVICE_MAC="AA-BB-${MAC_SEED:0:2}-${MAC_SEED:2:2}-EE-55"
TWO_DEV_MAC1="AA-BB-${MAC_SEED:0:2}-${MAC_SEED:2:2}-EE-71"
TWO_DEV_MAC2="AA-BB-${MAC_SEED:0:2}-${MAC_SEED:2:2}-EE-72"

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

## Запросы для ЛР 3

### 1) Активация лицензии и получение Ticket с ЭЦП (успех)

```bash
CREATE_SIGN=$(curl -k -s -X POST "$BASE_URL/api/licenses" \
  -H "Authorization: Bearer $ADMIN_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"productId\":1,\"typeId\":2,\"ownerId\":$USER1_ID,\"deviceCount\":1,\"description\":\"lab3-signature\"}")

CODE_SIGN=$(echo "$CREATE_SIGN" | jq -r '.code')

SIGNED_ACTIVATE=$(curl -k -s -X POST "$BASE_URL/api/licenses/activate" \
  -H "Authorization: Bearer $USER1_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"activationKey\":\"$CODE_SIGN\",\"deviceName\":\"Lab3 Laptop\",\"deviceMac\":\"$DEVICE1_MAC\"}")

echo "$SIGNED_ACTIVATE" | jq
```

### 2) Проверка лицензии и получение Ticket с ЭЦП (успех)

```bash
SIGNED_TICKET=$(curl -k -s -X POST "$BASE_URL/api/licenses/check" \
  -H "Authorization: Bearer $USER1_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"productId\":1,\"deviceMac\":\"$DEVICE1_MAC\"}")

echo "$SIGNED_TICKET" | jq
```

### 3) Проверка подписи Ticket публичным ключом (успех)

```bash
SIGNATURE_PUBLIC_KEY_BASE64=$(keytool -exportcert -rfc \
  -alias ticket-signing \
  -keystore src/main/resources/signature/signing.jks \
  -storepass admin11 \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | base64 | tr -d '\n')

CANONICAL_TICKET=$(echo "$SIGNED_TICKET" | jq -c '.ticket | to_entries | sort_by(.key) | from_entries')
SIGNATURE_B64=$(echo "$SIGNED_TICKET" | jq -r '.signature')

printf '%s' "$CANONICAL_TICKET" > /tmp/ticket.json
printf '%s' "$SIGNATURE_B64" | base64 -d > /tmp/ticket.sig
printf '%s' "$SIGNATURE_PUBLIC_KEY_BASE64" | base64 -d > /tmp/signature-public-key.der

openssl pkey -pubin -inform DER -in /tmp/signature-public-key.der -out /tmp/signature-public-key.pem >/dev/null 2>&1
openssl dgst -sha256 -verify /tmp/signature-public-key.pem -signature /tmp/ticket.sig /tmp/ticket.json
```

### 4) Проверка подписи после изменения Ticket (ошибка)

```bash
BROKEN_TICKET=$(echo "$SIGNED_TICKET" | jq -c '.ticket | .blocked |= not | to_entries | sort_by(.key) | from_entries')

printf '%s' "$BROKEN_TICKET" > /tmp/ticket-broken.json

openssl dgst -sha256 -verify /tmp/signature-public-key.pem -signature /tmp/ticket.sig /tmp/ticket-broken.json
```

### 5) Проверка подписи с повреждённой signature (ошибка)

```bash
BROKEN_SIGNATURE_B64="A${SIGNATURE_B64:1}"

printf '%s' "$BROKEN_SIGNATURE_B64" | base64 -d > /tmp/ticket-broken.sig

openssl dgst -sha256 -verify /tmp/signature-public-key.pem -signature /tmp/ticket-broken.sig /tmp/ticket.json
```

### 6) Проверка подписи чужим публичным ключом (ошибка)

```bash
openssl genpkey -algorithm RSA -out /tmp/lab3-other-private.pem -pkeyopt rsa_keygen_bits:2048
openssl pkey -in /tmp/lab3-other-private.pem -pubout -out /tmp/lab3-other-public.pem

openssl dgst -sha256 -verify /tmp/lab3-other-public.pem -signature /tmp/ticket.sig /tmp/ticket.json
```

### 7) Проверка подписи от другого Ticket (ошибка)

```bash
ACTIVATE_SIGNATURE_B64=$(echo "$SIGNED_ACTIVATE" | jq -r '.signature')

printf '%s' "$ACTIVATE_SIGNATURE_B64" | base64 -d > /tmp/activate-ticket.sig

openssl dgst -sha256 -verify /tmp/signature-public-key.pem -signature /tmp/activate-ticket.sig /tmp/ticket.json
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
  -d "{\"activationKey\":\"$CODE_LIMIT\",\"deviceName\":\"Laptop\",\"deviceMac\":\"$DEVICE1_MAC\"}")

echo "$ACTIVATE_LIMIT_OK" | jq
```

### 6) Повторная активация на том же устройстве (успех)

```bash
curl -k -X POST "$BASE_URL/api/licenses/activate" \
  -H "Authorization: Bearer $USER1_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"activationKey\":\"$CODE_LIMIT\",\"deviceName\":\"Laptop\",\"deviceMac\":\"$DEVICE1_MAC\"}"
```

### 7) Активация вторым устройством при лимите 1 (ошибка)

```bash
curl -k -i -X POST "$BASE_URL/api/licenses/activate" \
  -H "Authorization: Bearer $USER1_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"activationKey\":\"$CODE_LIMIT\",\"deviceName\":\"Phone\",\"deviceMac\":\"$DEVICE2_MAC\"}"
```

### 8) Активация лицензии другим пользователем (ошибка)

```bash
curl -k -i -X POST "$BASE_URL/api/licenses/activate" \
  -H "Authorization: Bearer $USER2_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"activationKey\":\"$CODE_LIMIT\",\"deviceName\":\"Other device\",\"deviceMac\":\"$OTHER_DEVICE_MAC\"}"
```

### 9) Активация с неверным ключом (ошибка)

```bash
curl -k -i -X POST "$BASE_URL/api/licenses/activate" \
  -H "Authorization: Bearer $USER1_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"activationKey\":\"BAD-KEY\",\"deviceName\":\"Laptop\",\"deviceMac\":\"$DEVICE1_MAC\"}"
```

### 10) Получение информации о лицензии владельцем (успех)

```bash
CHECK_OK=$(curl -k -s -X POST "$BASE_URL/api/licenses/check" \
  -H "Authorization: Bearer $USER1_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"productId\":1,\"deviceMac\":\"$DEVICE1_MAC\"}")

echo "$CHECK_OK" | jq
```

### 11) Получение информации о лицензии другим пользователем (ошибка)

```bash
curl -k -i -X POST "$BASE_URL/api/licenses/check" \
  -H "Authorization: Bearer $USER2_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"productId\":1,\"deviceMac\":\"$DEVICE1_MAC\"}"
```

### 12) Получение информации по несуществующему устройству (ошибка)

```bash
curl -k -i -X POST "$BASE_URL/api/licenses/check" \
  -H "Authorization: Bearer $USER1_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"productId\":1,\"deviceMac\":\"$MISSING_DEVICE_MAC\"}"
```

### 13) Продление слишком рано (ошибка)

```bash
curl -k -i -X POST "$BASE_URL/api/licenses/renew" \
  -H "Authorization: Bearer $USER1_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"activationKey\":\"$CODE_LIMIT\",\"deviceMac\":\"$DEVICE1_MAC\"}"
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
  -d "{\"activationKey\":\"$CODE_RENEW\",\"deviceName\":\"RenewDevice\",\"deviceMac\":\"$RENEW_DEVICE_MAC\"}" >/dev/null

docker exec postgres-db psql -U admin -d admin_bd -c \
  "update license set ending_date = now() + interval '1 day' where code = '$CODE_RENEW';"

curl -k -X POST "$BASE_URL/api/licenses/renew" \
  -H "Authorization: Bearer $USER1_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"activationKey\":\"$CODE_RENEW\",\"deviceMac\":\"$RENEW_DEVICE_MAC\"}"
```

### 15) Продление другим пользователем (ошибка)

```bash
curl -k -i -X POST "$BASE_URL/api/licenses/renew" \
  -H "Authorization: Bearer $USER2_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"activationKey\":\"$CODE_RENEW\",\"deviceMac\":\"$RENEW_DEVICE_MAC\"}"
```

### 16) Продление с несуществующим ключом (ошибка)

```bash
curl -k -i -X POST "$BASE_URL/api/licenses/renew" \
  -H "Authorization: Bearer $USER1_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"activationKey\":\"NO-SUCH-CODE\",\"deviceMac\":\"$RENEW_DEVICE_MAC\"}"
```

### 17) Продление с непривязанным устройством (ошибка)

```bash
curl -k -i -X POST "$BASE_URL/api/licenses/renew" \
  -H "Authorization: Bearer $USER1_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"activationKey\":\"$CODE_RENEW\",\"deviceMac\":\"$UNBOUND_DEVICE_MAC\"}"
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
  -d "{\"activationKey\":\"$CODE_TWO_DEV\",\"deviceName\":\"PC\",\"deviceMac\":\"$TWO_DEV_MAC1\"}" >/dev/null

curl -k -X POST "$BASE_URL/api/licenses/activate" \
  -H "Authorization: Bearer $USER1_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"activationKey\":\"$CODE_TWO_DEV\",\"deviceName\":\"Phone\",\"deviceMac\":\"$TWO_DEV_MAC2\"}"
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

## Запросы для ЛР 4. Управление сигнатурами

### Подготовка данных

```bash
BASE_URL="https://localhost:8443"
TS4=$(date +%s)

LAB4_ADMIN_USERNAME="lab4_admin_${TS4}"
LAB4_USER_USERNAME="lab4_user_${TS4}"
LAB4_PASSWORD="Admin123!"

curl -k -s -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$LAB4_ADMIN_USERNAME\",\"password\":\"$LAB4_PASSWORD\",\"role\":\"ADMIN\"}" >/dev/null

curl -k -s -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$LAB4_USER_USERNAME\",\"password\":\"$LAB4_PASSWORD\",\"role\":\"USER\"}" >/dev/null

LAB4_ADMIN_ACCESS=$(curl -k -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$LAB4_ADMIN_USERNAME\",\"password\":\"$LAB4_PASSWORD\"}" | jq -r '.accessToken')

LAB4_USER_ACCESS=$(curl -k -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$LAB4_USER_USERNAME\",\"password\":\"$LAB4_PASSWORD\"}" | jq -r '.accessToken')
```

### 1) Проверка структуры таблиц и связей PostgreSQL (успех)

```bash
docker exec postgres-db psql -U admin -d admin_bd \
  -c "select table_name from information_schema.tables where table_schema='public' and table_name in ('signatures','signatures_history','signatures_audit') order by table_name;" \
  -c "select tc.table_name, kcu.column_name, ccu.table_name as foreign_table, ccu.column_name as foreign_column from information_schema.table_constraints tc join information_schema.key_column_usage kcu on tc.constraint_name = kcu.constraint_name and tc.table_schema = kcu.table_schema join information_schema.constraint_column_usage ccu on ccu.constraint_name = tc.constraint_name and ccu.table_schema = tc.table_schema where tc.constraint_type='FOREIGN KEY' and tc.table_schema='public' and tc.table_name in ('signatures_history','signatures_audit') order by tc.table_name, kcu.column_name;"
```

### 2) Создание сигнатуры администратором (успех)

```bash
LAB4_CREATE=$(curl -k -s -X POST "$BASE_URL/api/signatures" \
  -H "Authorization: Bearer $LAB4_ADMIN_ACCESS" \
  -H "Content-Type: application/json" \
  -d '{"threatName":"Win.Test.Sample","firstBytesHex":"A1B2C3D4","remainderHashHex":"ABCD1234EF567890","remainderLength":1024,"fileType":"exe","offsetStart":0,"offsetEnd":128}')

LAB4_SIGNATURE_ID=$(echo "$LAB4_CREATE" | jq -r '.id')
LAB4_CREATE_DIGITAL_SIGNATURE=$(echo "$LAB4_CREATE" | jq -r '.digitalSignatureBase64')
LAB4_SINCE=$(echo "$LAB4_CREATE" | jq -r '.updatedAt')

echo "$LAB4_CREATE" | jq
```

### 3) Создание сигнатуры пользователем USER (ошибка)

```bash
curl -k -i -X POST "$BASE_URL/api/signatures" \
  -H "Authorization: Bearer $LAB4_USER_ACCESS" \
  -H "Content-Type: application/json" \
  -d '{"threatName":"User.Forbidden","firstBytesHex":"AA11","remainderHashHex":"BB22","remainderLength":1,"fileType":"bin","offsetStart":0,"offsetEnd":1}'
```

### 4) Получение полной базы без DELETED (успех)

```bash
curl -k -s -X GET "$BASE_URL/api/signatures" \
  -H "Authorization: Bearer $LAB4_USER_ACCESS" | jq

# Проверка, что только что созданная сигнатура есть в полной базе
curl -k -s -X GET "$BASE_URL/api/signatures" \
  -H "Authorization: Bearer $LAB4_USER_ACCESS" \
  | jq --arg id "$LAB4_SIGNATURE_ID" 'map(select(.id==$id)) | length'
```

### 5) Получение инкремента без since (ошибка)

```bash
curl -k -i -X GET "$BASE_URL/api/signatures/increment" \
  -H "Authorization: Bearer $LAB4_USER_ACCESS"
```

### 6) Получение инкремента по since (успех)

```bash
curl -k -s -X GET "$BASE_URL/api/signatures/increment?since=1970-01-01T00:00:00Z" \
  -H "Authorization: Bearer $LAB4_USER_ACCESS" | jq
```

### 7) Получение сигнатуры по идентификатору (успех)

```bash
curl -k -s -X GET "$BASE_URL/api/signatures/$LAB4_SIGNATURE_ID" \
  -H "Authorization: Bearer $LAB4_USER_ACCESS" | jq
```

### 8) Получение сигнатуры по списку UUID (успех)

```bash
curl -k -s -X POST "$BASE_URL/api/signatures/by-ids" \
  -H "Authorization: Bearer $LAB4_USER_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"ids\":[\"$LAB4_SIGNATURE_ID\"]}" | jq
```

### 9) Обновление сигнатуры администратором + пересчёт подписи (успех)

```bash
LAB4_UPDATE=$(curl -k -s -X PUT "$BASE_URL/api/signatures/$LAB4_SIGNATURE_ID" \
  -H "Authorization: Bearer $LAB4_ADMIN_ACCESS" \
  -H "Content-Type: application/json" \
  -d '{"threatName":"Win.Test.Sample.Updated","firstBytesHex":"A1B2C3D4EE","remainderHashHex":"ABCD1234EF567891","remainderLength":2048,"fileType":"dll","offsetStart":16,"offsetEnd":512}')

LAB4_UPDATE_DIGITAL_SIGNATURE=$(echo "$LAB4_UPDATE" | jq -r '.digitalSignatureBase64')

echo "$LAB4_UPDATE" | jq
test "$LAB4_CREATE_DIGITAL_SIGNATURE" != "$LAB4_UPDATE_DIGITAL_SIGNATURE" && echo "OK: подпись пересчитана"
```

### 10) Обновление сигнатуры пользователем USER (ошибка)

```bash
curl -k -i -X PUT "$BASE_URL/api/signatures/$LAB4_SIGNATURE_ID" \
  -H "Authorization: Bearer $LAB4_USER_ACCESS" \
  -H "Content-Type: application/json" \
  -d '{"threatName":"Denied.Update","firstBytesHex":"ABCD","remainderHashHex":"DCBA","remainderLength":10,"fileType":"exe","offsetStart":0,"offsetEnd":10}'
```

### 11) Получение истории по signatureId администратором (успех)

```bash
curl -k -s -X GET "$BASE_URL/api/signatures/$LAB4_SIGNATURE_ID/history" \
  -H "Authorization: Bearer $LAB4_ADMIN_ACCESS" | jq
```

### 12) Получение истории по signatureId пользователем USER (ошибка)

```bash
curl -k -i -X GET "$BASE_URL/api/signatures/$LAB4_SIGNATURE_ID/history" \
  -H "Authorization: Bearer $LAB4_USER_ACCESS"
```

### 13) Получение аудита по signatureId администратором (успех)

```bash
curl -k -s -X GET "$BASE_URL/api/signatures/$LAB4_SIGNATURE_ID/audit" \
  -H "Authorization: Bearer $LAB4_ADMIN_ACCESS" | jq
```

### 14) Получение аудита по signatureId пользователем USER (ошибка)

```bash
curl -k -i -X GET "$BASE_URL/api/signatures/$LAB4_SIGNATURE_ID/audit" \
  -H "Authorization: Bearer $LAB4_USER_ACCESS"
```

### 15) Логическое удаление сигнатуры (успех)

```bash
curl -k -i -X DELETE "$BASE_URL/api/signatures/$LAB4_SIGNATURE_ID" \
  -H "Authorization: Bearer $LAB4_ADMIN_ACCESS"
```

### 16) Полная выгрузка не содержит DELETED (успех)

```bash
curl -k -s -X GET "$BASE_URL/api/signatures" \
  -H "Authorization: Bearer $LAB4_USER_ACCESS" | jq

# Важно: ответ может быть пустым []
curl -k -s -X GET "$BASE_URL/api/signatures" \
  -H "Authorization: Bearer $LAB4_USER_ACCESS" \
  | jq --arg id "$LAB4_SIGNATURE_ID" 'map(select(.id==$id)) | length'
```

### 17) Инкремент содержит DELETED (успех)

```bash
curl -k -s -X GET "$BASE_URL/api/signatures/increment?since=$LAB4_SINCE" \
  -H "Authorization: Bearer $LAB4_USER_ACCESS" | jq
```

### 18) Удаление несуществующей сигнатуры (ошибка)

```bash
curl -k -i -X DELETE "$BASE_URL/api/signatures/00000000-0000-0000-0000-000000000000" \
  -H "Authorization: Bearer $LAB4_ADMIN_ACCESS"
```
