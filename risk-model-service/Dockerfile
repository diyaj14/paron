FROM python:3.14-slim

WORKDIR /app

COPY risk-model-service/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY risk-model-service/app/ ./app/
COPY risk-model-service/artifacts/ ./artifacts/

EXPOSE 8600

CMD ["sh", "-c", "exec uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8600}"]
