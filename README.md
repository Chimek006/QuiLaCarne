# QuiLaCarne

QuiLaCarne to natywna aplikacja Android przygotowana dla kelnerów restauracji. Aplikacja pozwala pracować z salą, stolikami, rezerwacjami, zamówieniami i menu także wtedy, gdy połączenie z serwerem jest chwilowo niedostępne. Dane są przechowywane lokalnie w szyfrowanej bazie SQLite, a po odzyskaniu połączenia aplikacja synchronizuje się z API.

## Najważniejsze funkcje

- logowanie kelnera i przechowywanie sesji użytkownika,
- praca offline na lokalnej bazie danych,
- synchronizacja menu, stolików, użytkowników, rezerwacji, zamówień i pozycji zamówień,
- podgląd sali oraz statusów stolików,
- zmiana statusu stolika,
- obsługa rezerwacji przypisanych do stolików,
- dodawanie i usuwanie dań z rezerwacji,
- podgląd menu, składników i alergenów,
- zgłaszanie problematycznego klienta,
- powiadomienia systemowe o istotnych zmianach, między innymi o przypisaniu kelnera i gotowości dań,
- automatyczne odświeżanie tokenów i ponowne uwierzytelnianie sesji offline po odzyskaniu połączenia.

## Wymagania

Do uruchomienia projektu potrzeba:

- Android Studio,
- JDK obsługiwanego przez aktualną wersję Android Studio,
- Android SDK z platformą API 35,
- emulatora Androida albo fizycznego telefonu,
- dostępu do API QuiLaCarne (internetu).

Minimalna wersja Androida aplikacji to API 24.

## API

Dokumentacja API znajduje się tutaj:

https://mateusz-bogacz-collegiumwitelona.github.io/QuiLaCarne_API/

Domyślny adres API używany przez aplikację:

```properties
https://api.quilacarne.com.pl/api/
```

## Uruchomienie aplikacji

### 1. Klonowanie repozytorium

```bash
git clone -b done https://github.com/Chimek006/QuiLaCarne.git
cd QuiLaCarne
```

### 2. Otwieranie projektu w Android Studio

Należy otworzyć folder projektu jako projekt Android/Gradle. Android Studio powinno automatycznie pobrać zależności i zsynchronizować Gradle.

### 3. Konfiguracja `local.properties`

W głównym katalogu projektu należy skopiować plik `local.properties.example` i uzupełnić go o brakujące pola.

Przykład dla Windowsa:

```properties
sdk.dir=C\:\\Users\\TWOJ_USER\\AppData\\Local\\Android\\Sdk
BASE_URL=https://api.quilacarne.com.pl/api/
WEBSOCKET_URL=
```

Przykład dla Linuxa/macOS:

```properties
sdk.dir=/home/TWOJ_USER/Android/Sdk
BASE_URL=https://api.quilacarne.com.pl/api/
WEBSOCKET_URL=
```

`BASE_URL` jest wymagany, jeśli chcesz wskazać konkretny adres API. Jeżeli go nie podasz, aplikacja użyje domyślnego adresu `https://api.quilacarne.com.pl/api/`.


### 4. Uruchomienie aplikacji

W Android Studio należy wybrać konfigurację `app`, następnie uruchom projekt na emulatorze lub telefonie.

Możesz też użyć Gradle z terminala:

```bash
./gradlew assembleDebug
```

Na Windowsie:

```bash
gradlew.bat assembleDebug
```

Plik APK po zbudowaniu powinien znajdować się w:

```text
app/build/outputs/apk/debug/
```

## Testy i analiza kodu

Uruchomienie testów jednostkowych:

```bash
./gradlew test
```

Na Windowsie:

```bash
gradlew.bat test
```

Uruchomienie Detekta:

```bash
./gradlew detekt
```

Uruchomienie raportów Kover:

```bash
./gradlew koverHtmlReport
```

## Konfiguracja bezpieczeństwa

Aplikacja korzysta z kilku mechanizmów zabezpieczających:

1. **Szyfrowanie offline** - lokalna baza Room/SQLite jest otwierana przez SQLCipher z hasłem generowanym dla aplikacji.
2. **Android Keystore** - hasło do bazy i tokeny są przechowywane w `EncryptedSharedPreferences`, zabezpieczonych kluczem `MasterKey` z Android Keystore.
3. **Synchronizacja przez UUID** - aplikacja mapuje tokeny/identyfikatory z API na stabilne lokalne UUID, dzięki czemu może bezpiecznie łączyć dane lokalne z danymi z serwera.
4. **Powiadomienia** - aplikacja używa uprawnienia `POST_NOTIFICATIONS` i lokalnych helperów powiadomień, aby informować kelnera o istotnych zmianach w pracy restauracji.
