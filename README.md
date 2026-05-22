# View3D

Polska dokumentacja aplikacji Android do przeglądania i edycji modeli 3D z lokalnych plików.

## Opis

View3D pozwala otwierać modele 3D z pamięci urządzenia, wyświetlać je w scenie i korygować ich położenie bezpośrednio w aplikacji.

## Obsługiwane formaty

- `GLB`
- `GLTF`
- `STL`
- `OBJ`

## Instalacja

### W Android Studio

1. Otwórz projekt `view3d` w Android Studio.
2. Poczekaj na synchronizację Gradle.
3. Uruchom aplikację na emulatorze albo urządzeniu.

### Z wiersza poleceń

1. Przejdź do katalogu projektu.
2. Uruchom build:

```bash
./gradlew assembleDebug
```

3. Zainstaluj wygenerowany plik APK na urządzeniu albo uruchom projekt z poziomu Android Studio.

## Podstawowy przepływ

1. Uruchom aplikację.
2. Kliknij `Załaduj`.
3. Wybierz lokalny plik modelu 3D.
4. Poczekaj na zakończenie ładowania.
5. Otwórz `Sterowanie`, aby dopasować pozycję, obrót i skalę.

## Sterowanie widokiem

- Przesuń palcem po ekranie, aby obracać kamerę.
- Użyj gestu szczypania, aby przybliżać i oddalać widok.
- Model jest automatycznie wyśrodkowany po załadowaniu.

## Pasek sterowania

Panel sterowania jest ukryty domyślnie. Po otwarciu:

- można go przeciągać za nagłówek,
- zawiera sekcje `Przesuń` i `Obróć`,
- ma przycisk `Wstecz` w obu sekcjach,
- zawiera suwak `Skala`,
- ma przycisk `Reset` do przywrócenia ustawień domyślnych.

## Przesuwanie modelu

W sekcji `Przesuń` znajdziesz trzy suwaki:

- `X` - ruch w lewo i prawo,
- `Y` - ruch w górę i dół,
- `Z` - ruch do przodu i do tyłu.

Przycisk `Wyśrodkuj` zeruje przesunięcie.

## Obracanie modelu

W sekcji `Obróć` są trzy suwaki:

- `Oś X` - obrót wokół osi X,
- `Oś Y` - obrót wokół osi Y,
- `Oś Z` - obrót wokół osi Z.

## Skala

Suwak `Skala` zmienia rozmiar modelu w zakresie od `0.2` do `5.0`.

## Reset

Przycisk `Reset` przywraca domyślne wartości:

- przesunięcie `0, 0, 0`,
- obrót `0, 0, 0`,
- skala `1`.

## Komunikaty i ładowanie

- Modele są kopiowane do cache aplikacji przed otwarciem.
- Jeśli plik nie może zostać odczytany, aplikacja pokaże komunikat o błędzie.
- Dla `GLB` i `GLTF` aplikacja próbuje załadować model bezpośrednio.
- `STL` i `OBJ` są parsowane i renderowane jako siatka.

## Przykłady użycia

### Szybki podgląd modelu

1. Kliknij `Załaduj`.
2. Wybierz plik `chair.glb`.
3. Obróć kamerę, aby obejrzeć model z różnych stron.

### Korekta modelu STL

1. Załaduj plik `scan.stl`.
2. Otwórz `Sterowanie`.
3. Wejdź w `Przesuń`.
4. Ustaw `Y`, aby model leżał poprawnie na podłożu.
5. Wróć i dopasuj `Skala`, jeśli model jest za duży.

### Ustawienie obiektu do prezentacji

1. Załaduj `product.obj`.
2. Otwórz `Sterowanie`.
3. Przejdź do `Obróć`.
4. Ustaw model pod kątem, który najlepiej pokazuje detale.
5. Użyj `Wyśrodkuj`, jeśli obiekt ma wrócić do środka sceny.

## Wskazówki

- Jeśli model GLTF ma pliki towarzyszące, trzymaj je razem w tym samym folderze.
- Jeśli plik nie pojawia się od razu, poczekaj na zakończenie ładowania.
- Przy bardzo dużych modelach warto najpierw użyć `Skala`, a dopiero potem dopracować pozycję.
