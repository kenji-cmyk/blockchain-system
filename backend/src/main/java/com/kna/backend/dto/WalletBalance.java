package com.kna.backend.dto;

import com.kna.backend.pkg.money.MoneyUnits;

public record WalletBalance(String address, double balance, long balanceUnits) {
    public WalletBalance(String address, double balance) {
        this(address, balance, MoneyUnits.toUnits(balance));
    }
}
