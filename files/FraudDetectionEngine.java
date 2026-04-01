package finguard.engine;

import finguard.model.FraudAlert;
import finguard.model.Transaction;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Core concurrent fraud detection engine.
 *
 * Thread architecture:
 *   Thread-1 (Ingestion)  : Puts transactions onto ingestQueue
 *   Thread-2 (Analyzer)   : Drains ingestQueue → updates AccountWindows → pushes alerts
 *   Thread-3 (Dispatcher) : Drains alertQueue → invokes registered alert listeners
 *
 * Shared state is protected via:
 *   - ConcurrentHashMap for accountWindows (lock striping by key)
 *   - BlockingQueues for inter-thread communication (lock-free)
 *   - Per-AccountWindow synchronization for window mutation
 */
public class FraudDetectionEngine {

    // ── Queues ─────────────────────────────────────────────────────────────
    private final BlockingQueue<Transaction> ingestQueue = new LinkedBlockingQueue<>(100_000);
    private final BlockingQueue<FraudAlert>  alertQueue  = new LinkedBlockingQueue<>(50_000);

    // ── Per-account rolling windows ────────────────────────────────────────
    private final Map<String, AccountWindow> accountWindows = new ConcurrentHashMap<>();

    // ── Services ───────────────────────────────────────────────────────────
    private final RiskScorer scorer = new RiskScorer();

    // ── State ──────────────────────────────────────────────────────────────
    private volatile boolean running = false;
    private Thread analyzerThread;
    private Thread dispatcherThread;

    // ── Metrics ────────────────────────────────────────────────────────────
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong alertCount     = new AtomicLong(0);
    private final AtomicLong fraudCount     = new AtomicLong(0);

    // ── Alert listener ─────────────────────────────────────────────────────
    private Consumer<FraudAlert> alertListener = alert -> {}; // no-op default

    public void setAlertListener(Consumer<FraudAlert> listener) {
        this.alertListener = listener;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────
    public void start() {
        running = true;

        analyzerThread = new Thread(this::analyzerLoop, "FinGuard-Analyzer");
        analyzerThread.setDaemon(true);
        analyzerThread.start();

        dispatcherThread = new Thread(this::dispatcherLoop, "FinGuard-Dispatcher");
        dispatcherThread.setDaemon(true);
        dispatcherThread.start();
    }

    public void stop() throws InterruptedException {
        running = false;
        if (analyzerThread  != null) analyzerThread.interrupt();
        if (dispatcherThread!= null) dispatcherThread.interrupt();
    }

    /** Thread-1: submit transaction for processing (called by ingestion layer) */
    public void submit(Transaction t) throws InterruptedException {
        ingestQueue.put(t);
    }

    /** Non-blocking submit (returns false if queue full) */
    public boolean trySubmit(Transaction t) {
        return ingestQueue.offer(t);
    }

    // ── Thread-2: Analyzer ─────────────────────────────────────────────────
    private void analyzerLoop() {
        while (running || !ingestQueue.isEmpty()) {
            try {
                Transaction t = ingestQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (t == null) continue;

                // Get or create account window
                AccountWindow win = accountWindows.computeIfAbsent(
                        t.getAccountId(), AccountWindow::new);

                // Update rolling window (synchronized inside AccountWindow)
                win.addAndEvict(t);

                processedCount.incrementAndGet();

                // Only score DEBIT transactions (risk events)
                if (t.getType() == Transaction.TransactionType.DEBIT) {
                    FraudAlert alert = scorer.evaluate(win);
                    if (alert.getSeverity() != FraudAlert.Severity.SAFE) {
                        alertQueue.offer(alert);
                        alertCount.incrementAndGet();
                        if (alert.getSeverity() == FraudAlert.Severity.FRAUD_ALERT) {
                            fraudCount.incrementAndGet();
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // ── Thread-3: Dispatcher ───────────────────────────────────────────────
    private void dispatcherLoop() {
        while (running || !alertQueue.isEmpty()) {
            try {
                FraudAlert alert = alertQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (alert == null) continue;
                alertListener.accept(alert);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // ── Metrics accessors ──────────────────────────────────────────────────
    public long getProcessedCount() { return processedCount.get(); }
    public long getAlertCount()     { return alertCount.get(); }
    public long getFraudCount()     { return fraudCount.get(); }
    public int  getIngestQueueSize(){ return ingestQueue.size(); }
    public int  getAccountCount()   { return accountWindows.size(); }
    public Map<String, AccountWindow> getAccountWindows() { return accountWindows; }
}
