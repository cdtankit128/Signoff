import asyncio
import time
import requests
import logging
from bleak import BleakScanner
from bleak.backends.device import BLEDevice
from bleak.backends.scanner import AdvertisementData

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(message)s')

# Target BLE properties
TARGET_UUID = "0000180F-0000-1000-8000-00805f9b34fb"
SERVER_URL = "http://localhost:8080/api/ble"

def send_heartbeat(device_name: str, rssi: int):
    try:
        payload = {
            "deviceId": device_name or "SignOff-BLE-Device",
            "rssi": rssi,
            "timestamp": int(time.time() * 1000)
        }
        res = requests.post(SERVER_URL, json=payload, timeout=2)
        if res.status_code != 200:
            logging.warning(f"Server returned {res.status_code}")
    except requests.exceptions.RequestException as e:
        logging.error(f"Failed to reach Spring Boot server: {e}")

def detection_callback(device: BLEDevice, advertisement_data: AdvertisementData):
    # Check if this advertisement contains our target Service UUID
    if TARGET_UUID.lower() in [str(u).lower() for u in advertisement_data.service_uuids]:
        logging.info(f"Target found: {device.name} | RSSI: {device.rssi}")
        send_heartbeat(device.name, device.rssi)

async def scan_loop():
    logging.info("Starting resilient BLE Scanner loop...")
    scanner = BleakScanner(detection_callback)
    
    while True:
        try:
            logging.info("Initializing BLE Adapter...")
            await scanner.start()
            logging.info("Scanning for SignOff Beacon...")
            
            # Keep scanner alive indefinitely
            while True:
                await asyncio.sleep(5.0)
                
        except Exception as e:
            logging.error(f"BLE Scanner crashed: {e}. Retrying in 5 seconds...")
            try:
                await scanner.stop()
            except:
                pass
            await asyncio.sleep(5.0)

if __name__ == "__main__":
    try:
        asyncio.run(scan_loop())
    except KeyboardInterrupt:
        logging.info("Scanner terminated by user.")
