# Dokumentacja aplikacji QuiLaCarne

## 1. Cel aplikacji

QuiLaCarne to aplikacja mobilna dla kelnerów, której zadaniem jest usprawnienie codziennej obsługi restauracji. Aplikacja pozwala kelnerowi sprawdzić stan sali, obsługiwać stoliki i rezerwacje, pracować z menu oraz aktualizować zamówienia.

Projekt został przygotowany jako aplikacja Android oparta o lokalną bazę danych i API restauracji. Dzięki temu aplikacja może działać również wtedy, gdy urządzenie chwilowo utraci dostęp do internetu. Po odzyskaniu połączenia dane są synchronizowane z API.

## 2. Charakter aplikacji

Aplikacja jest przeznaczona głównie dla kelnerów. Jej funkcjonalności są dopasowane do pracy na sali restauracyjnej, gdzie szybki dostęp do informacji jest ważniejszy niż rozbudowany panel administracyjny.

Najważniejsze obszary działania:

- obsługa logowania pracownika,
- pobieranie danych z API,
- lokalne przechowywanie danych,
- praca offline,
- synchronizacja danych po odzyskaniu połączenia,
- podgląd stolików i ich statusów,
- obsługa rezerwacji,
- obsługa zamówień i pozycji zamówień,
- podgląd menu, składników i alergenów,
- zgłaszanie problematycznych klientów,
- odświeżanie aktywnych ekranów przez synchronizację REST.

## 3. Technologie

Projekt korzysta z następujących technologii:

- Kotlin - główny język aplikacji,
- Jetpack Compose - budowa interfejsu użytkownika,
- Material 3 - komponenty UI,
- Navigation Compose - nawigacja między ekranami,
- Room - lokalna warstwa bazy danych,
- SQLite + SQLCipher - szyfrowana lokalna baza danych,
- Retrofit - komunikacja HTTP z API,
- OkHttp - klient HTTP, interceptory i authenticator,
- Android Security Crypto - bezpieczne przechowywanie tokenów i haseł,
- Android Keystore - ochrona kluczy szyfrujących,
- Coil - ładowanie obrazów,
- Detekt - statyczna analiza kodu,
- Kover - raporty pokrycia testów.

## 4. API

Aplikacja komunikuje się z API QuiLaCarne.

Dokumentacja API:

https://mateusz-bogacz-collegiumwitelona.github.io/QuiLaCarne_API/

API udostępnia między innymi endpointy dla roli kelnera, takie jak:

- pobieranie menu restauracji,
- pobieranie listy stolików,
- dodawanie dań do rezerwacji,
- zgłaszanie problematycznego klienta,
- zmiana statusu stolika,
- przypisanie kelnera do rezerwacji,
- oznaczenie rezerwacji jako nieobecnej,
- synchronizacja użytkowników, stolików, rezerwacji, zamówień, pozycji zamówień, składników i dań,
- pobieranie manifestu synchronizacji.

## 5. Tryb offline

Jednym z najważniejszych założeń aplikacji jest możliwość pracy offline.

Po zalogowaniu i pobraniu danych aplikacja zapisuje najważniejsze informacje lokalnie w bazie Room. Dzięki temu kelner może dalej korzystać z aplikacji nawet wtedy, gdy połączenie z serwerem chwilowo nie działa.

Lokalnie przechowywane są między innymi:

- użytkownicy,
- stoliki,
- statusy stolików,
- rezerwacje,
- zamówienia,
- pozycje zamówień,
- dania,
- składniki,
- alergeny,
- kategorie dań,
- powiązania składników, alergenów i dań.

Po odzyskaniu połączenia aplikacja sprawdza dostępność serwera i uruchamia synchronizację. Jeżeli sesja była offline, aplikacja próbuje ponownie uwierzytelnić użytkownika.

## 6. Synchronizacja danych

Synchronizacja opiera się na komunikacji z API oraz lokalnym mapowaniu danych. Aplikacja pobiera dane stronicowane z endpointów synchronizacyjnych i zapisuje je w lokalnej bazie.

Synchronizowane są między innymi:

- menu,
- składniki,
- alergeny,
- kategorie,
- stoliki,
- statusy stolików,
- użytkownicy,
- rezerwacje,
- zamówienia,
- pozycje zamówień.

Publiczna dokumentacja API nie opisuje kanału WebSocket, STOMP ani SSE. Aplikacja nie tworzy więc sztucznego klienta WebSocket; aktualność danych zapewnia synchronizacja REST po zalogowaniu, po wejściu w ekrany operacyjne, po akcjach zmieniających stan oraz polling z bezpiecznym interwałem wyłącznie na aktywnych ekranach.

Aplikacja korzysta z tokenów/identyfikatorów z API i mapuje je na stabilne UUID po stronie lokalnej. Pozwala to utrzymywać spójność między danymi lokalnymi i zdalnymi.

Przykładowy mechanizm:

```kotlin
private fun String.toStableUUID(): UUID = UUID.nameUUIDFromBytes(toByteArray())
```

Takie podejście pozwala odtworzyć ten sam lokalny identyfikator dla tego samego tokenu z API, bez losowania nowego UUID przy każdej synchronizacji.

## 7. Bezpieczeństwo

### 7.1. Szyfrowanie offline

Lokalna baza danych SQLite jest szyfrowana przy użyciu SQLCipher. Aplikacja korzysta z Room, ale baza jest otwierana przez `SupportFactory`, który dostaje hasło wygenerowane dla aplikacji.

Najważniejszy efekt: dane zapisane offline nie są przechowywane jako zwykły, łatwy do odczytania plik SQLite.

### 7.2. Android Keystore

Hasło do bazy danych oraz tokeny są zapisywane w bezpiecznych preferencjach opartych o Android Security Crypto.

Aplikacja tworzy `MasterKey` w schemacie `AES256_GCM`, a następnie używa `EncryptedSharedPreferences` z szyfrowaniem kluczy `AES256_SIV` i wartości `AES256_GCM`.

W praktyce oznacza to, że wrażliwe dane nie są przechowywane w zwykłym `SharedPreferences` bez ochrony. Android Keystore odpowiada za ochronę klucza głównego używanego do szyfrowania.

### 7.3. Tokeny i sesja

Tokeny logowania są przechowywane w zaszyfrowanych preferencjach. Aplikacja zapisuje access token, refresh token oraz informacje potrzebne do pracy użytkownika.

Dodatkowo w warstwie sieciowej działa:

- `AuthInterceptor` - dodaje token do zapytań wymagających autoryzacji,
- `TokenAuthenticator` - obsługuje odświeżanie tokenu,
- `RemoteTokenStore` - przechowuje tokeny zdalnych encji i mapowania na lokalne UUID.

### 7.4. Synchronizacja przez UUID

Dane z API są identyfikowane tokenami. W aplikacji tokeny są zamieniane na stabilne UUID. Dzięki temu lokalna baza może bezpiecznie łączyć obiekty, nawet jeżeli dane pochodzą z wielu synchronizacji.

Mechanizm ten zmniejsza ryzyko duplikowania rekordów i pozwala zachować relacje między encjami, na przykład między stolikiem, rezerwacją, zamówieniem i pozycjami zamówienia.

### 7.5. HTTPS

Aplikacja wymusza bezpieczny adres API w buildach release. Jeżeli build nie jest debugowy, `BASE_URL` musi zaczynać się od `https://`.

Dodatkowo w manifeście ustawiono `android:usesCleartextTraffic="false"`, co blokuje zwykły nieszyfrowany ruch HTTP.

## 8. Architektura aplikacji

Projekt jest podzielony na kilka głównych warstw.

### 8.1. Warstwa UI

Warstwa interfejsu znajduje się w pakiecie `ui`.

Zawiera między innymi:

- ekrany aplikacji,
- motyw aplikacji,
- ViewModele,
- obsługę języka i wyświetlania danych użytkownikowi.

Przykładowe ekrany:

- `LoginScreen`,
- `MainScreen`,
- `TablesScreen`,
- `MenuScreen`,
- `TableDetailScreen`,
- `OrderAddScreen`,
- `ReportClientScreen`,
- `DishDetailScreen`,
- `SyncScreen`.

### 8.2. Warstwa lokalna

Warstwa lokalna znajduje się w `data/local`.

Zawiera:

- `AppDatabase.kt` - konfigurację bazy Room,
- DAO - dostęp do danych,
- encje - struktury tabel lokalnej bazy,
- konwertery typów,
- `TokenManager` - obsługę tokenów użytkownika.

Baza zawiera między innymi encje:

- `UsersEntity`,
- `RestaurantTableEntity`,
- `TableStatusEntity`,
- `DishEntity`,
- `IngredientEntity`,
- `AllergenEntity`,
- `OrderEntity`,
- `OrderItemEntity`,
- `ReservationEntity`,
- `GuestReportEntity`.

### 8.3. Warstwa zdalna

Warstwa zdalna znajduje się w `data/remote`.

Zawiera:

- definicje endpointów Retrofit w `api`,
- DTO używane do komunikacji z API,
- konfigurację klienta sieciowego,
- interceptory i authenticator,
- przechowywanie mapowań tokenów z API.

Najważniejsze serwisy API:

- `AuthService`,
- `TableService`,
- `OrderService`,
- `DishService`.

### 8.4. Repozytoria

Repozytoria znajdują się w `data/repository` i łączą warstwę lokalną z API.

Najważniejsze repozytoria odpowiadają za:

- logowanie,
- synchronizację danych,
- obsługę zamówień,
- obsługę stolików,
- rozwiązywanie konfliktów statusów.

## 9. Struktura plików

```text
QuiLaCarne/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/example/quilacarne/
│           ├── MainActivity.kt
│           ├── data/
│           │   ├── local/
│           │   │   ├── AppDatabase.kt
│           │   │   ├── TokenManager.kt
│           │   │   ├── dao/
│           │   │   ├── entities/
│           │   │   └── relations/
│           │   ├── remote/
│           │   │   ├── api/
│           │   │   ├── dto/
│           │   │   └── network/
│           │   └── repository/
│           ├── ui/
│           │   ├── screens/
│           │   ├── theme/
│           │   ├── viewmodels/
│           │   └── i18n/
│           └── utils/
├── config/
│   └── detekt/
├── gradle/
│   └── libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
└── local.properties
```

## 10. Główne przepływy działania

### 10.1. Start aplikacji

Po uruchomieniu aplikacji wykonywane są najważniejsze inicjalizacje:

1. prośba o uprawnienie do powiadomień,
2. inicjalizacja monitora sieci,
3. inicjalizacja klienta Retrofit,
4. inicjalizacja ustawień języka,
5. uruchomienie obserwatora połączenia,
6. otwarcie szyfrowanej bazy danych,
7. start interfejsu Compose.

### 10.2. Logowanie

Użytkownik loguje się do aplikacji przy pomocy danych pracownika. Po poprawnym logowaniu aplikacja zapisuje tokeny w bezpiecznych preferencjach i może pobrać dane potrzebne do pracy offline.

### 10.3. Synchronizacja początkowa

Po zalogowaniu aplikacja może pobrać pełniejszy zestaw danych z API. Synchronizacja obejmuje dane potrzebne kelnerowi do pracy na sali.

### 10.4. Praca offline

Gdy nie ma połączenia, aplikacja nadal korzysta z lokalnej bazy. Użytkownik może przeglądać dane zapisane na urządzeniu i kontynuować pracę w zakresie obsługiwanym przez lokalny stan aplikacji.

### 10.5. Powrót online

Po odzyskaniu połączenia aplikacja:

1. sprawdza dostępność serwera,
2. próbuje ponownie uwierzytelnić sesję offline, jeżeli jest taka potrzeba,
3. uruchamia synchronizację danych,
4. aktualizuje lokalną bazę.

## 11. Konfiguracja projektu

Adres API jest konfigurowany przez `local.properties`.

Przykład:

```properties
BASE_URL=https://api.quilacarne.com.pl/api/
```

W Gradle wartości te są przepisywane do `BuildConfig`:

```kotlin
buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
```

Jeżeli `BASE_URL` nie zostanie podany, aplikacja używa domyślnego adresu API.

## 12. Testy i jakość kodu

Projekt posiada konfigurację narzędzi:

- Detekt - analiza statyczna kodu Kotlin,
- Kover - raportowanie pokrycia testami,
- JUnit i Robolectric - testy jednostkowe i testy zależne od Androida.

Przykładowe komendy:

```bash
./gradlew test
./gradlew detekt
./gradlew koverHtmlReport
```

Na Windowsie:

```bash
gradlew.bat test
gradlew.bat detekt
gradlew.bat koverHtmlReport
```

## 13. Podsumowanie

QuiLaCarne jest aplikacją wspierającą pracę kelnerów w restauracji. Jej najważniejszą cechą jest połączenie pracy offline z bieżącą synchronizacją z API. Dzięki lokalnej szyfrowanej bazie danych, bezpiecznemu przechowywaniu tokenów i mapowaniu identyfikatorów przez UUID aplikacja jest przygotowana do praktycznego użycia w środowisku, gdzie połączenie internetowe może być czasami niestabilne.
