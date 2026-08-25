# Iran Gold Tracker

Telegram bot that reports Iranian gold, currency and cryptocurrency prices from the
[BRS](https://brsapi.ir) API and notifies users when a price leaves a range they chose.

## Features

- Inline menu with gold / currency / crypto price lists, formatted as `طلای 18 عیار : 22,242,700 تومان`.
- Market data is fetched once a minute into an in-memory cache; every handler and the alert
  watcher read from that cache, so a Telegram click never triggers an API call.
- Prices are stamped with the real Jalali date and Tehran time.
- Per-user price alerts (max 3 per Telegram user), stored in H2 through JPA.
- A scheduler checks the cached prices every minute and pings the user when a watched price
  is at or outside their band, then deletes the alert.

## Requirements

- Java 21
- Maven (or the bundled `./mvnw`)
- A Telegram bot token from [@BotFather](https://t.me/BotFather)
- A BRS API key

## Configuration

`src/main/resources/application.yml` imports a `.env` file from the repository root, so the app
does not start without it. `.env` is gitignored — never commit real keys.

```properties
BOT_TOKEN=123456:telegram-bot-token
BRS_API_KEY=brs-api-key
# base URL only, the client appends /Market/Gold_Currency.php
BRS_API_URL=https://BrsApi.ir/Api
DB_URL=jdbc:h2:file:./data/irangoldtracker
DB_USERNAME=sa
DB_PASSWORD=
```

## Run

```bash
./mvnw spring-boot:run
```

Then send `/start` (or `/menu`) to the bot in Telegram to get the main menu. The H2 console is
enabled at `/h2-console`.

## Using the bot

| Button | What happens |
| --- | --- |
| 💵 قیمت ارز / 💵 قیمت ارز دیجیتال / 🥇 قیمت طلا | Sends `بذار ببینم قیمتا چطورین الان میگم بهت، صبر کن`, then the price list |
| ➕ افزودن هشدار | Starts the alert wizard |
| 🔔 مدیریت هشدار | Lists your alerts, each with a delete button |

### Adding an alert

The wizard asks one question at a time and only returns to the main menu when the flow ends:

1. **Market** — buttons: طلا / ارز / ارز دیجیال.
2. **Item** — buttons listing that market's items with their current price, 8 per page
   (⬅️ قبلی / بعدی ➡️, 🔙 بازارها to go back). Items you already watch are not listed.
3. **Floor price** (`کف`) — must be a positive number. Persian/Arabic digits and `,` are accepted.
4. **Ceiling price** (`سقف`) — must be greater than the floor.

Anything invalid keeps you on the same step. Send `لغو`, `بیخیال`, `cancel`, `/cancel` or tap
✖️ لغو to abort. You cannot have two alerts for the same item, and the third alert is the last one.

### When an alert fires

Once the cached price is at or outside the band, the bot sends
`بدو بدو <item> اومده رو <price>` followed by `این هشدار رو حذف کردم چون قیمتش رو دیدم ✅`,
and the alert row is deleted — it is never checked again. If sending fails the alert is kept and
retried on the next check.

## Project layout

```
bot/            GoldBot long-polling entry point, message sender, main menu
bot/handler/    Price handlers, AddAlertHandler (wizard), ManageAlertHandler (list/delete)
service/        MarketPriceService (BRS client), MarketPriceCache (1-min refresh),
                AlertService (users/alerts), AlertWatcher (1-min check)
entity/         TelegramUser, Alert
repository/     Spring Data repositories
dto/            MarketResponse, MarketItem, MarketItemMatch
utils/          Utils (formatting, item lookup, price parsing), JalaliDateTime, MarketCurrencies
```

## Build

```bash
./mvnw -B compile
./mvnw -B test
```

## Upgrading an existing database

Schema is managed by `ddl-auto: update`, which adds columns but never drops them. A database
created before alert statuses were removed still has a `NOT NULL` `status` column, which makes
every new alert insert fail. Run once against such a database:

```sql
ALTER TABLE alert DROP COLUMN status;
ALTER TABLE alert DROP COLUMN triggered_at;
ALTER TABLE alert DROP COLUMN triggered_price;
```
