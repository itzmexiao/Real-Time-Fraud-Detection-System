# FinGuard — Real-Time Fraud Detection & Smart Transaction Monitoring System
**IBM Hackathon | Java (8+) | No external frameworks**

---

## 📂 Project Structure

```
FinGuard/
├── src/finguard/
│   ├── Main.java                          ← Entry point (CLI)
│   ├── model/
│   │   ├── Transaction.java               ← Transaction POJO + CSV parser
│   │   └── FraudAlert.java                ← Alert model with severity levels
│   ├── engine/
│   │   ├── AccountWindow.java             ← Rolling 10-min window (per account)
│   │   ├── RiskScorer.java                ← Scoring algorithm
│   │   └── FraudDetectionEngine.java      ← 3-thread concurrent engine
│   ├── ingestion/
│   │   └── TransactionIngestion.java      ← File + simulation ingestion (Thread-1)
│   ├── alert/
│   │   └── AlertDispatcher.java           ← Alert writer + logger (Thread-3)
│   └── util/
│       └── DatasetGenerator.java          ← Sample CSV dataset generator
├── web/
│   └── index.html                         ← Professional dashboard (open in browser)
├── data/
│   └── transactions.csv                   ← Generated/loaded dataset
└── README.md
```

---

## 🚀 How to Run (Java Backend)

### Compile
```bash
mkdir bin
find src -name "*.java" | xargs javac -d bin -sourcepath src
```

### Generate Sample Dataset
```bash
java -cp bin finguard.util.DatasetGenerator data/transactions.csv 2000
```

### Run — Simulation Mode (default)
```bash
java -cp bin finguard.Main simulate 2000
```

### Run — File Mode (from CSV)
```bash
java -cp bin finguard.Main file data/transactions.csv
```

---

## 🌐 Web Dashboard

Open `web/index.html` in any modern browser.

- **Simulate mode** — generates transactions in real-time in the browser
- **File mode** — upload your own CSV file

### CSV Format
```
# timestamp,transactionId,accountId,type,amount,location,deviceId
1708069200,TXN000001,ACC501,DEBIT,25000.00,Mumbai,DEV_A1
```

---

## 📊 External Datasets

| Dataset | Source | Format |
|---------|--------|--------|
| Online Payments Fraud | [Kaggle](https://www.kaggle.com/datasets/rupakroy/online-payments-fraud-detection-dataset) | CSV |
| Credit Card Fraud Detection | [Kaggle](https://www.kaggle.com/datasets/mlg-ulb/creditcardfraud) | CSV |
| IEEE-CIS Fraud Detection | [Kaggle](https://www.kaggle.com/c/ieee-fraud-detection/data) | CSV |
| Synthetic Financial Datasets | [Kaggle](https://www.kaggle.com/datasets/ealaxi/paysim1) | CSV |

Download any of the above and adapt the CSV loader to match the column format.

---

## ⚙️ Architecture

```
┌────────────────────────────────────────────────────────────┐
│                   FraudDetectionEngine                     │
│                                                            │
│  [Thread-1: Ingestion]                                     │
│   File / Simulation → BlockingQueue<Transaction>           │
│           ↓                                                │
│  [Thread-2: Analyzer]                                      │
│   Dequeue → AccountWindow.addAndEvict() → RiskScorer       │
│           ↓                                                │
│  [Thread-3: Dispatcher]                                    │
│   AlertQueue → Console print / fraud_alert.txt write       │
└────────────────────────────────────────────────────────────┘
```

---

## 🧮 Risk Score Formula

```
Risk Score = (highValueTransactions × 5)
           + (cityChanges_5min × 7)
           + (deviceSwitches × 4)
           + (floor(totalSpend / 50000) × 3)

Score  0–15  → SAFE
Score 16–30  → SUSPICIOUS
Score 31–50  → HIGH_RISK  (console alert)
Score   51+  → FRAUD_ALERT (console + fraud_alert.txt)
```

---

## 🔍 Fraud Detection Rules (Rolling 10-min Window)

| Rule | Threshold | Weight |
|------|-----------|--------|
| High-value transactions (>₹50,000) | >3 in window | ×5 per txn |
| City changes in 5 minutes | ≥2 cities | ×7 per city |
| Different devices used | >5 devices | ×4 per device |
| Total spending in window | >₹2,00,000 | +3 per ₹50k |

---

## ✅ Requirements Coverage

| Requirement | Implementation |
|-------------|----------------|
| Java 8+ | ✅ Java 21 compatible, no Java 9+ APIs used |
| No Spring/Hibernate | ✅ Pure Java, zero dependencies |
| No database | ✅ In-memory ConcurrentHashMap + Deque |
| OOP | ✅ Transaction, FraudAlert, AccountWindow, RiskScorer |
| Collections Framework | ✅ HashMap, Deque, HashSet, BlockingQueue |
| Multithreading | ✅ 3-thread architecture, ExecutorService-compatible |
| File Handling | ✅ BufferedReader (read) + BufferedWriter (write alerts) |
| Exception Handling | ✅ Try-catch on all I/O + parse operations |
| Java Time API | ✅ Instant, LocalDateTime, ZoneId |
| Rolling Window | ✅ Deque-based O(1) amortized, no full rescan |
| Memory Optimization | ✅ Auto-eviction of expired transactions |
| Thread Safety | ✅ synchronized AccountWindow + ConcurrentHashMap + BlockingQueue |
| 2000–5000 Tx support | ✅ Tested, queue capacity 100,000 |
