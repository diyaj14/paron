# Paron

## Overview
Paron is a distributed offline payment settlement system aligned with RBI'S digital ruppee intiative. Its been designed to handle fund reservation,JWT-based offline token spending,Idempotent kafka driven settlement and fraud detection 

## Architecture

```mermaid
%%{init: {'theme':'base', 'themeVariables': { 'fontSize': '14px', 'fontFamily': 'Arial'}}}%%
graph LR
    %% Define Styles for Phases
    classDef phase1 fill:#00FF00,stroke:#009900,color:#000000,font-weight:bold; %% Green
    classDef phase2 fill:#FFB6C1,stroke:#CC6699,color:#000000,font-weight:bold; %% Pink
    classDef phase3 fill:#FFD54F,stroke:#CC9900,color:#000000,font-weight:bold; %% Yellow

    %% Define Styles for Steps
    classDef step1 fill:#66FF66,stroke:#009900,color:#000000,border-radius:10px;
    classDef step2 fill:#FFCCE5,stroke:#CC6699,color:#000000,border-radius:10px;
    classDef step3 fill:#FFE082,stroke:#CC9900,color:#000000,border-radius:10px;

    %% --- PHASE 1 COLUMN ---
    subgraph P1 ["Phase 1: Online"]
        direction TB
        S1["<b>Step 1</b><br>User requests<br>offline limit"]
        S2["<b>Step 2</b><br>Bank reserves funds<br>Issues signed JWT"]
        S3["<b>Step 3</b><br>Token stored on<br>device securely"]
        S1 --> S2 --> S3
    end
    class P1 phase1

    %% --- PHASE 2 COLUMN ---
    subgraph P2 ["Phase 2: Offline"]
        direction TB
        S4["<b>Step 4</b><br>User pays merchant<br>via QR or BT"]
        S5["<b>Step 5</b><br>Transaction saved<br>locally on device"]
        S6["<b>Step 6</b><br>JWT limit decrements<br>on each spend"]
        S4 --> S5 --> S6
    end
    class P2 phase2

    %% --- PHASE 3 COLUMN ---
    subgraph P3 ["Phase 3: Reconnect"]
        direction TB
        S7["<b>Step 7</b><br>Device pushes queued<br>TXNs to Kafka"]
        S8["<b>Step 8</b><br>Spring Batch validates<br>and settles via NPCI"]
        S9["<b>Step 9</b><br>Reservation released<br>ledger updated"]
        S7 --> S8 --> S9
    end
    class P3 phase3

    %% --- HORIZONTAL ALIGNMENT OF PHASES ---
    P1 --> P2 --> P3

    %% --- APPLY STEP STYLES ---
    class S1,S2,S3 step1;
    class S4,S5,S6 step2;
    class S7,S8,S9 step3;
```
## Project Structure

```text
offline-payment-system/
├── token-service/               # Issues and validates JWT spending tokens
│   ├── src/main/java/
│   │   ├── controller/          # REST API endpoints
│   │   ├── service/             # Business logic
│   │   ├── model/               # Token data models
│   │   └── security/            # JWT signing and verification
│   └── pom.xml
├── ledger-service/              # Talks to bank, reserves/releases funds
│   ├── src/main/java/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/          # JPA repos for PostgreSQL
│   │   └── model/
│   └── pom.xml
├── sync-service/                # Receives offline TXNs when device reconnects
│   ├── src/main/java/
│   │   ├── kafka/               # Kafka consumers and producers
│   │   ├── batch/               # Spring Batch settlement jobs
│   │   ├── idempotency/         # Prevents duplicate processing
│   │   └── service/
│   └── pom.xml
├── fraud-service/               # Scores each transaction for risk
│   ├── src/main/java/
│   │   ├── rules/               # Rule-based checks
│   │   ├── ml/                  # Simple ML scoring (optional)
│   │   └── service/
│   └── pom.xml
├── api-gateway/                 # Single entry point, routes all requests
│   └── src/main/java/
├── docker-compose.yml           # Runs everything locally together
├── README.md                    # Your portfolio showcase document
└── docs/
    ├── architecture.md
    └── api-spec.yaml            # OpenAPI/Swagger docs
```

