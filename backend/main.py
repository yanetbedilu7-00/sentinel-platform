from fastapi import FastAPI
from pydantic import BaseModel
from datetime import datetime
import sqlite3
import os

app = FastAPI(title="Sentinel Trust API")

DB_PATH = "sentinel.db"

def init_db():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS telemetry (
            device_id TEXT,
            model TEXT,
            android_version TEXT,
            fingerprint TEXT,
            bootloader_status TEXT,
            safety_net_result TEXT,
            installed_apps_count INTEGER,
            timestamp TEXT
        )
    """)
    conn.commit()
    conn.close()

init_db()

class DeviceTelemetry(BaseModel):
    device_id: str
    model: str
    android_version: str
    fingerprint: str
    bootloader_status: str
    safety_net_result: str
    installed_apps_count: int
    timestamp: datetime

@app.post("/api/v1/telemetry")
async def receive_telemetry(data: DeviceTelemetry):
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute("""
        INSERT INTO telemetry VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """, (data.device_id, data.model, data.android_version,
          data.fingerprint, data.bootloader_status,
          data.safety_net_result, data.installed_apps_count,
          data.timestamp.isoformat()))
    conn.commit()
    conn.close()
    
    trust_score = 100
    if data.bootloader_status == "UNLOCKED":
        trust_score -= 30
    if data.safety_net_result == "FAILED":
        trust_score -= 40
    
    return {
        "trust_score": trust_score,
        "risk_level": "HIGH" if trust_score < 50 else "MEDIUM" if trust_score < 80 else "LOW",
        "recommendation": "Review device integrity" if trust_score < 80 else "Device appears trustworthy"
    }

@app.get("/api/v1/device/{device_id}/history")
async def get_device_history(device_id: str):
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM telemetry WHERE device_id = ? ORDER BY timestamp DESC", (device_id,))
    rows = cursor.fetchall()
    conn.close()
    
    history = []
    for row in rows:
        history.append({
            "device_id": row[0], "model": row[1], "android_version": row[2],
            "fingerprint": row[3], "bootloader_status": row[4],
            "safety_net_result": row[5], "installed_apps_count": row[6],
            "timestamp": row[7]
        })
    return {"device_id": device_id, "history": history}

@app.get("/")
async def root():
    return {"message": "Sentinel API is running"}
