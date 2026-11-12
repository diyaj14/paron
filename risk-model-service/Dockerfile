FROM python:3.14-slim

WORKDIR /app

COPY risk-model-service/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY risk-model-service/app/ ./app/
COPY risk-model-service/artifacts/ ./artifacts/
COPY ml/artifacts/scenario-v2/ ./artifacts/scenario-v2/

# app/main.py loads the promoted scenario-v2 model from this directory.
ENV MODEL_DIR=/app/artifacts/scenario-v2

EXPOSE 8600

CMD ["sh", "-c", "exec uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8600}"]
