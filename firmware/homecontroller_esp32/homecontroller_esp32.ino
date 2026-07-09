// HomeController — firmware ESP32
// Expose un service BLE : LED (write) + Compteur (notify).
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#define LED_PIN 2  // LED intégrée sur la plupart des ESP32 DevKit ; ajuster si besoin

#define SERVICE_UUID      "990c9205-4132-4360-aa80-2bd5ce8d6e93"
#define LED_CHAR_UUID     "d64abbf2-4dab-4198-8a93-fb7348943972"
#define COUNTER_CHAR_UUID "2a554b2a-5f4b-4c89-9a59-e4d8f6d4b9d8"

BLECharacteristic* counterCharacteristic = nullptr;
bool deviceConnected = false;
uint32_t counter = 0;
unsigned long lastTick = 0;

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* server) override { deviceConnected = true; }
  void onDisconnect(BLEServer* server) override {
    deviceConnected = false;
    server->getAdvertising()->start();  // ré-annonce pour permettre une reconnexion
  }
};

class LedCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* characteristic) override {
    // ESP32 Arduino core 3.x : getValue() renvoie un String Arduino (plus un std::string).
    String value = characteristic->getValue();
    if (value.length() > 0) {
      digitalWrite(LED_PIN, value[0] == 0x01 ? HIGH : LOW);
    }
  }
};

void setup() {
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  BLEDevice::init("HomeController-ESP32");
  BLEServer* server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  BLEService* service = server->createService(SERVICE_UUID);

  BLECharacteristic* ledCharacteristic = service->createCharacteristic(
      LED_CHAR_UUID, BLECharacteristic::PROPERTY_WRITE);
  ledCharacteristic->setCallbacks(new LedCallbacks());

  counterCharacteristic = service->createCharacteristic(
      COUNTER_CHAR_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  counterCharacteristic->addDescriptor(new BLE2902());  // CCCD requis pour notify

  service->start();

  BLEAdvertising* advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->setScanResponse(true);
  BLEDevice::startAdvertising();
}

void loop() {
  if (deviceConnected && millis() - lastTick >= 1000) {
    lastTick = millis();
    counter++;
    uint8_t payload[4];  // little-endian
    payload[0] = counter & 0xFF;
    payload[1] = (counter >> 8) & 0xFF;
    payload[2] = (counter >> 16) & 0xFF;
    payload[3] = (counter >> 24) & 0xFF;
    counterCharacteristic->setValue(payload, 4);
    counterCharacteristic->notify();
  }
}
