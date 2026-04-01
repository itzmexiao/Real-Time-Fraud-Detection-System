package finguard.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Represents a financial transaction in the FinGuard system.
 * Format: timestamp,transactionId,accountId,type,amount,location,deviceId
 */
public class Transaction implements Comparable<Transaction> {

    public enum TransactionType {
        DEBIT, CREDIT
    }

    private final long timestamp;         // epoch seconds
    private final String transactionId;
    private final String accountId;
    private final TransactionType type;
    private final double amount;
    private final String location;
    private final String deviceId;

    public Transaction(long timestamp, String transactionId, String accountId,
                       TransactionType type, double amount, String location, String deviceId) {
        this.timestamp     = timestamp;
        this.transactionId = transactionId;
        this.accountId     = accountId;
        this.type          = type;
        this.amount        = amount;
        this.location      = location;
        this.deviceId      = deviceId;
    }

    // ── Getters ────────────────────────────────────────────────────────────
    public long   getTimestamp()     { return timestamp; }
    public String getTransactionId() { return transactionId; }
    public String getAccountId()     { return accountId; }
    public TransactionType getType() { return type; }
    public double getAmount()        { return amount; }
    public String getLocation()      { return location; }
    public String getDeviceId()      { return deviceId; }

    /** Returns epoch-ms for Java Time API compatibility */
    public long getTimestampMillis() { return timestamp * 1000L; }

    public LocalDateTime getDateTime() {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());
    }

    @Override
    public int compareTo(Transaction o) {
        return Long.compare(this.timestamp, o.timestamp);
    }

    @Override
    public String toString() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return String.format("[%s] %s | Acc:%-8s | %-6s | ₹%10.2f | %-12s | %s",
                getDateTime().format(fmt), transactionId, accountId,
                type, amount, location, deviceId);
    }

    /**
     * Parse a CSV line:
     * timestamp,transactionId,accountId,type,amount,location,deviceId
     */
    public static Transaction parse(String csvLine) throws IllegalArgumentException {
        String[] p = csvLine.trim().split(",");
        if (p.length < 7) throw new IllegalArgumentException("Invalid CSV: " + csvLine);
        try {
            long   ts     = Long.parseLong(p[0].trim());
            String tid    = p[1].trim();
            String aid    = p[2].trim();
            TransactionType type = TransactionType.valueOf(p[3].trim().toUpperCase());
            double amount = Double.parseDouble(p[4].trim());
            String loc    = p[5].trim();
            String devId  = p[6].trim();
            return new Transaction(ts, tid, aid, type, amount, loc, devId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Parse error in line: " + csvLine, e);
        }
    }
}
