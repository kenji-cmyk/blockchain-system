package com.kna.backend.entity;

import com.kna.backend.pkg.money.MoneyUnits;

public record UtxoEntry(String transactionId, int outputIndex, String owner, double amount) {
    public long amountUnits() {
        return MoneyUnits.toUnits(amount);
    }
}
