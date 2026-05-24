package com.kna.backend.entity;

import com.kna.backend.pkg.utils.CryptoUtil;
import com.kna.backend.pkg.utils.StringUtil;

public class Transaction {

    public static final String SYSTEM_SENDER = "SYSTEM";

    private final String sender;
    private final String receiver;
    private final double amount;
    private final double fee;
    private final long timeStamp;
    private String signature;
    private final String transactionId;

    public Transaction(String sender, String receiver, double amount) {
        this(sender, receiver, amount, 0);
    }

    public Transaction(String sender, String receiver, double amount, double fee) {
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
        this.fee = fee;
        this.timeStamp = System.currentTimeMillis();
        this.transactionId = calculateTransactionId();
    }

    public static Transaction miningReward(String receiver, double amount) {
        Transaction transaction = new Transaction(SYSTEM_SENDER, receiver, amount);
        transaction.signature = "SYSTEM_REWARD";
        return transaction;
    }

    public void sign(String privateKey) {
        if (isMiningReward()) {
            return;
        }
        this.signature = CryptoUtil.sign(privateKey, signingPayload());
    }

    public boolean isValid() {
        if (isMiningReward()) {
            return receiver != null && !receiver.isBlank() && amount > 0 && fee == 0;
        }
        if (sender == null || sender.isBlank() || receiver == null || receiver.isBlank()) {
            return false;
        }
        if (amount <= 0 || fee < 0 || signature == null || signature.isBlank()) {
            return false;
        }
        return CryptoUtil.verify(sender, signingPayload(), signature);
    }

    public boolean isMiningReward() {
        return SYSTEM_SENDER.equals(sender);
    }

    private String calculateTransactionId() {
        return StringUtil.applySha256(signingPayload());
    }

    private String signingPayload() {
        return sender + "|" + receiver + "|" + amount + "|" + fee + "|" + timeStamp;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public double getAmount() {
        return amount;
    }

    public double getFee() {
        return fee;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public String getSignature() {
        return signature;
    }

    public String getTransactionId() {
        return transactionId;
    }
}
