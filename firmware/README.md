# Firmware HomeController — ESP32

Sketch BLE : caractéristique LED (write) + caractéristique Compteur (notify).

## Prérequis (une seule fois)
1. Installer l'Arduino IDE.
2. Fichier → Préférences → « URL de gestionnaire de cartes supplémentaires » :
   `https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json`
3. Outils → Type de carte → Gestionnaire de cartes → installer **esp32** (Espressif).

## Flasher
1. Ouvrir `homecontroller_esp32/homecontroller_esp32.ino`.
2. Outils → Type de carte → **ESP32 Dev Module** (ou la carte ELEGOO ESP32).
3. Brancher l'ESP32 en USB, sélectionner le **Port**.
   (Si le port n'apparaît pas : installer le pilote USB CP2102 ou CH340.)
4. Cliquer **Téléverser** (flèche).

## Vérifier
- L'ESP32 apparaît dans un scanner BLE sous `HomeController-ESP32`.
- La LED `GPIO 2` répond aux écritures ; le compteur s'incrémente chaque seconde.
- Si la LED intégrée n'est pas sur GPIO 2 sur ta carte : modifier `#define LED_PIN`.

## Profil GATT (contrat partagé avec l'app)
| Élément | UUID | Propriété | Format |
|---|---|---|---|
| Service | `990c9205-4132-4360-aa80-2bd5ce8d6e93` | — | — |
| LED | `d64abbf2-4dab-4198-8a93-fb7348943972` | Write | 1 octet : `0x00`/`0x01` |
| Compteur | `2a554b2a-5f4b-4c89-9a59-e4d8f6d4b9d8` | Notify | entier 32 bits little-endian |
