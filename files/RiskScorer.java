package finguard.engine;

import finguard.model.FraudAlert;
import finguard.model.FraudAlert.Severity;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless scorer. Given an AccountWindow snapshot, produces a FraudAlert.
 *
 * Risk Score Formula:
 *   = (highValueTransactions × 5)
 *   + (cityChanges × 7)
 *   + (deviceSwitches × 4)
 *   + totalSpendingFactor
 *
 * totalSpendingFactor = floor(totalSpending / 50000) × 3  (max contribution ~12 for 2L)
 */
public class RiskScorer {

    private static final double HIGH_VALUE_THRESHOLD   = 50_000.0;
    private static final double TOTAL_SPEND_THRESHOLD  = 200_000.0;
    private static final int    HIGH_VAL_LIMIT         = 3;    // trigger if >3
    private static final int    CITY_LIMIT             = 2;    // 2 different cities in 5 min
    private static final int    DEVICE_LIMIT           = 5;    // >5 different devices

    /**
     * Evaluate the account window and produce an alert (always returns one, even SAFE).
     */
    public FraudAlert evaluate(AccountWindow win) {
        int highValueCount  = win.getHighValueCount();
        int cityChanges5Min = win.getCityChangesIn5Min();
        int deviceSwitches  = win.getDeviceSwitches();
        double totalSpend   = win.getTotalSpending();

        // totalSpendingFactor: every ₹50k contributes 3 points
        int totalSpendFactor = (int)(totalSpend / HIGH_VALUE_THRESHOLD) * 3;

        int score = (highValueCount  * 5)
                  + (cityChanges5Min * 7)
                  + (deviceSwitches  * 4)
                  + totalSpendFactor;

        List<String> reasons = new ArrayList<>();
        if (highValueCount > HIGH_VAL_LIMIT)
            reasons.add(highValueCount + " high-value transactions (>₹50k)");
        if (cityChanges5Min >= CITY_LIMIT)
            reasons.add(cityChanges5Min + " cities within 5 minutes");
        if (deviceSwitches > DEVICE_LIMIT)
            reasons.add(deviceSwitches + " different devices");
        if (totalSpend > TOTAL_SPEND_THRESHOLD)
            reasons.add(String.format("Total spend ₹%.0f exceeds ₹2,00,000", totalSpend));

        String reason = reasons.isEmpty()
                ? "Normal activity"
                : String.join("; ", reasons);

        Severity severity = FraudAlert.scoreToSeverity(score);

        return new FraudAlert(
                win.getAccountId(), score, severity, reason,
                highValueCount, cityChanges5Min, deviceSwitches, totalSpend);
    }
}
