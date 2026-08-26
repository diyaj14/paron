"""Paron Guard Risk Model Service — FastAPI application.

Serves POST /v1/score and GET /healthz.
Loads only from artifacts/approved/<version>/.
"""

import os
import time
from pathlib import Path
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse
from joblib import load
import numpy as np
from pydantic import BaseModel, Field

MODEL_DIR = Path(os.getenv("MODEL_DIR", "./artifacts/approved"))
SUPPORTED_SCHEMA_VERSIONS = ["v1"]
MODEL_VERSION = "lr-calibrated-v1.0.0"
THRESHOLD_POLICY_VERSION = "thresholds-v1"

FEATURE_NAMES = [
    "amount", "amount_to_token_limit_ratio", "merchant_amount_deviation",
    "user_tx_count_5m", "user_tx_count_1h", "user_tx_count_24h",
    "device_tx_count_5m", "device_tx_count_1h", "device_tx_count_24h",
    "token_tx_count_24h", "user_tx_value_1h", "device_tx_value_24h",
    "token_age_seconds", "time_to_expiry_seconds", "offline_duration_seconds",
    "token_reuse_count", "duplicate_payload_hash_count", "previous_settlement_failed",
    "merchant_risk_aggregate", "hour_of_day", "day_of_week",
    "history_available", "token_age_known", "expiry_known",
]

REASON_CODE_MAP = {
    "amount": ("AMOUNT_DEVIATION_HIGH", "Amount is much higher than this merchant's usual payments"),
    "amount_to_token_limit_ratio": ("AMOUNT_DEVIATION_HIGH", "Amount is much higher than this merchant's usual payments"),
    "merchant_amount_deviation": ("AMOUNT_DEVIATION_HIGH", "Amount is much higher than this merchant's usual payments"),
    "user_tx_count_5m": ("VELOCITY_ELEVATED", "Unusually many payments in a short window"),
    "user_tx_count_1h": ("VELOCITY_ELEVATED", "Unusually many payments in a short window"),
    "user_tx_count_24h": ("VELOCITY_ELEVATED", "Unusually many payments in a short window"),
    "device_tx_count_5m": ("VELOCITY_ELEVATED", "Unusually many payments in a short window"),
    "device_tx_count_1h": ("VELOCITY_ELEVATED", "Unusually many payments in a short window"),
    "device_tx_count_24h": ("VELOCITY_ELEVATED", "Unusually many payments in a short window"),
    "user_tx_value_1h": ("VALUE_SPIKE_1H", "Unusually high total value within the last hour"),
    "device_tx_value_24h": ("VALUE_SPIKE_1H", "Unusually high total value within the last hour"),
    "token_reuse_count": ("TOKEN_REUSE_DETECTED", "This token shows signs of being used before"),
    "duplicate_payload_hash_count": ("DUPLICATE_PAYLOAD", "An identical submission already exists"),
    "time_to_expiry_seconds": ("TOKEN_NEAR_EXPIRY", "Token was close to expiry when submitted"),
    "offline_duration_seconds": ("OFFLINE_DURATION_LONG", "Payment stayed offline unusually long before syncing"),
    "hour_of_day": ("ODD_HOUR_PATTERN", "Unusual time of day for this merchant"),
    "previous_settlement_failed": ("PREVIOUS_SETTLEMENT_FAILED", "Recent settlement failures for this user/device"),
    "merchant_risk_aggregate": ("MERCHANT_RISK_ELEVATED", "Merchant has elevated historical dispute rate"),
    "history_available": ("HISTORY_MISSING", "Insufficient history — low signal quality"),
}

model = None


def load_model():
    global model
    for version_dir in sorted(MODEL_DIR.iterdir()):
        if version_dir.is_dir():
            model_path = version_dir / "model.joblib"
            if model_path.exists():
                model = load(model_path)
                return True
    return False


@asynccontextmanager
async def lifespan(app: FastAPI):
    if not load_model():
        raise RuntimeError("No approved model found in artifacts/approved/")
    yield


app = FastAPI(title="Paron Guard Risk Model Service", lifespan=lifespan)


class ScoreRequest(BaseModel):
    correlationId: str
    featureSchemaVersion: str
    features: dict


class Contribution(BaseModel):
    reasonCode: str
    plainLanguage: str
    weight: float


class ScoreResponse(BaseModel):
    correlationId: str
    score: float
    confidence: float
    fallback: bool
    modelVersion: str
    thresholdPolicyVersion: str
    featureSchemaVersion: str
    topContributions: list[Contribution]


def compute_confidence(score: float) -> float:
    distance = abs(score - 0.5) * 2
    return round(min(0.5 + distance * 0.5, 1.0), 4)


def get_top_contributions(features: dict, coefficients: np.ndarray) -> list[Contribution]:
    raw_weights = []
    for i, name in enumerate(FEATURE_NAMES):
        value = features.get(name, 0.0)
        if value is None:
            value = 0.0
        raw_weights.append(coefficients[i] * value)

    total_abs = sum(abs(w) for w in raw_weights)
    if total_abs == 0:
        return []

    contributions = []
    for i, name in enumerate(FEATURE_NAMES):
        normalized = raw_weights[i] / total_abs
        if abs(normalized) > 0.01:
            code, lang = REASON_CODE_MAP.get(name, ("UNKNOWN", "Unknown factor"))
            contributions.append(Contribution(reasonCode=code, plainLanguage=lang, weight=round(float(normalized), 4)))

    contributions.sort(key=lambda c: abs(c.weight), reverse=True)
    seen = set()
    unique = []
    for c in contributions:
        if c.reasonCode not in seen:
            seen.add(c.reasonCode)
            unique.append(c)
    return unique[:3]


@app.post("/v1/score", response_model=ScoreResponse)
async def score(request: ScoreRequest):
    if request.featureSchemaVersion not in SUPPORTED_SCHEMA_VERSIONS:
        raise HTTPException(status_code=409, detail={
            "error": {
                "code": "SCHEMA_VERSION_UNSUPPORTED",
                "message": f"model supports: {', '.join(SUPPORTED_SCHEMA_VERSIONS)}, got: {request.featureSchemaVersion}"
            }
        })

    missing = [f for f in FEATURE_NAMES if f not in request.features]
    extra = [f for f in request.features if f not in FEATURE_NAMES]
    if missing:
        raise HTTPException(status_code=400, detail={
            "error": {"code": "MISSING_FEATURE", "message": f"missing features: {missing}"}
        })
    if extra:
        raise HTTPException(status_code=400, detail={
            "error": {"code": "UNKNOWN_FEATURE", "message": f"unknown features: {extra}"}
        })

    feature_values = []
    for name in FEATURE_NAMES:
        val = request.features.get(name)
        if val is None:
            feature_values.append(0.0)
        else:
            feature_values.append(float(val))

    X = np.array([feature_values])
    start = time.perf_counter()
    proba = model.predict_proba(X)[0, 1]
    elapsed_ms = (time.perf_counter() - start) * 1000

    if elapsed_ms > 150:
        raise HTTPException(status_code=500, detail={
            "error": {"code": "INTERNAL_ERROR", "message": "model inference exceeded 150ms budget"}
        })

    score_val = round(float(proba), 4)
    confidence = compute_confidence(score_val)

    coefficients = model.calibrated_classifiers_[0].estimator.named_steps["clf"].coef_[0]
    contributions = get_top_contributions(request.features, coefficients)

    return ScoreResponse(
        correlationId=request.correlationId,
        score=score_val,
        confidence=confidence,
        fallback=False,
        modelVersion=MODEL_VERSION,
        thresholdPolicyVersion=THRESHOLD_POLICY_VERSION,
        featureSchemaVersion=request.featureSchemaVersion,
        topContributions=contributions,
    )


@app.get("/healthz")
async def healthz():
    return {
        "status": "ok",
        "modelVersion": MODEL_VERSION,
        "featureSchemaVersions": SUPPORTED_SCHEMA_VERSIONS,
    }


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    return JSONResponse(
        status_code=500,
        content={"error": {"code": "INTERNAL_ERROR", "message": str(exc)}},
    )
