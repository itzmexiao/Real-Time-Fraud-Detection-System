package finguard.alert;

import finguard.model.FraudAlert;
import finguard.model.FraudAlert.Severity;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * Alert Dispatcher — Thread-3 role.
 *
 * Responsibilities:
 *   - Print HIGH_RISK alerts to console
 *   - Write FRAUD_ALERT entries to fraud_alert.txt
 *   - Maintain in-memory alert history for dashboard queries
 */
public class AlertDispatcher {

    private static final Logger LOG = Logger.getLogger(AlertDispatcher.class.getName());
    private static final String ALERT_FILE = "fraud_alert.txt";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final List<FraudAlert>           history     = new ArrayList<>();
    private final ReentrantReadWriteLock     rwLock      = new ReentrantReadWriteLock();
    private final String                     alertFile;

    public AlertDispatcher() {
        this(ALERT_FILE);
    }

    public AlertDispatcher(String alertFilePath) {
        this.alertFile = alertFilePath;
        // Ensure file exists / is writable
        try {
            Files.createDirectories(Paths.get(alertFilePath).getParent() == null
                    ? Paths.get(".") : Paths.get(alertFilePath).getParent());
            if (!Files.exists(Paths.get(alertFilePath))) {
                Files.createFile(Paths.get(alertFilePath));
            }
        } catch (IOException e) {
            LOG.warning("Cannot initialize alert file: " + e.getMessage());
        }
    }

    /**
     * Primary dispatch method. Called by engine's dispatcher thread.
     */
    public void dispatch(FraudAlert alert) {
        // Store in history
        rwLock.writeLock().lock();
        try { history.add(alert); }
        finally { rwLock.writeLock().unlock(); }

        // Console output for HIGH_RISK+
        if (alert.getSeverity() == Severity.HIGH_RISK
                || alert.getSeverity() == Severity.FRAUD_ALERT) {
            printAlert(alert);
        }

        // File write for FRAUD_ALERT only
        if (alert.getSeverity() == Severity.FRAUD_ALERT) {
            writeToFile(alert);
        }
    }

    private void printAlert(FraudAlert alert) {
        String border = alert.getSeverity() == Severity.FRAUD_ALERT
                ? "████████████████████████████████████████"
                : "----------------------------------------";
        System.out.println(border);
        System.out.printf("[%s] %s%n", alert.getSeverity(), LocalDateTime.now().format(FMT));
        System.out.printf("Account  : %s%n", alert.getAccountId());
        System.out.printf("Score    : %d%n", alert.getRiskScore());
        System.out.printf("Reason   : %s%n", alert.getReason());
        System.out.printf("Details  : HV=%d  Cities=%d  Devices=%d  Spend=₹%.0f%n",
                alert.getHighValueCount(), alert.getCityChanges(),
                alert.getDeviceSwitches(), alert.getTotalSpending());
        System.out.println(border);
    }

    private synchronized void writeToFile(FraudAlert alert) {
        try (BufferedWriter bw = new BufferedWriter(
                new FileWriter(alertFile, true))) {
            bw.write(alert.toFileRecord());
            bw.newLine();
            bw.flush();
        } catch (IOException e) {
            LOG.severe("Failed to write fraud alert to file: " + e.getMessage());
        }
    }

    /** Read-safe snapshot of alert history */
    public List<FraudAlert> getHistory() {
        rwLock.readLock().lock();
        try { return Collections.unmodifiableList(new ArrayList<>(history)); }
        finally { rwLock.readLock().unlock(); }
    }

    public long countBySeverity(Severity sev) {
        rwLock.readLock().lock();
        try { return history.stream().filter(a -> a.getSeverity() == sev).count(); }
        finally { rwLock.readLock().unlock(); }
    }

    public String getAlertFilePath() { return alertFile; }
}
