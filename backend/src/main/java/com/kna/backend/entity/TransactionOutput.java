package com.kna.backend.entity;

import com.kna.backend.pkg.money.MoneyUnits;

public record TransactionOutput(String receiver, double amount) {
    public long amountUnits() {
        return MoneyUnits.toUnits(amount);
    }
}
