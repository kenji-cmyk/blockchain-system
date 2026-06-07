package com.kna.backend.entity;

import com.kna.backend.pkg.utils.CryptoUtil;
import com.kna.backend.pkg.utils.StringUtil;
import com.kna.backend.pkg.money.MoneyUnits;

import java.util.List;
import java.util.UUID;

public class Transaction {

    public static final String SYSTEM_SENDER = "SYSTEM";

    private final String sender;
    private final String receiver;
    private final double amount;
    private final double fee;
    private final long timeStamp;
    private String signature;
    private final String transactionId;
    private final String nonce;
    private final List<TransactionInput> inputs;
    private final List<TransactionOutput> outputs;

    public Transaction(String sender, String receiver, double amount) {
        this(sender, receiver, amount, 0);
    }

    public Transaction(String sender, String receiver, double amount, double fee) {
        this(sender, receiver, amount, fee, List.of(), List.of(new TransactionOutput(receiver, amount)));
    }

    public Transaction(
            String sender,
            String receiver,
            double amount,
            double fee,
            List<TransactionInput> inputs,
            List<TransactionOutput> outputs
    ) {
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
        this.fee = fee;
        this.timeStamp = System.currentTimeMillis();
        this.nonce = UUID.randomUUID().toString();
        this.inputs = List.copyOf(inputs);
        this.outputs = List.copyOf(outputs);
        this.transactionId = calculateTransactionId();
    }

    public static Transaction miningReward(String receiver, double amount) {
        Transaction transaction = new Transaction(
                SYSTEM_SENDER,
                receiver,
                amount,
                0,
                List.of(),
                List.of(new TransactionOutput(receiver, amount))
        );
        transaction.signature = "SYSTEM_REWARD";
        return transaction;
    }

    public void sign(String privateKey) {
        if (isMiningReward()) {
            return;
        }
        this.signature = CryptoUtil.sign(privateKey, getSigningPayload());
    }

    public boolean isValid() {
        if (transactionId == null || !transactionId.equals(calculateTransactionId())) {
            return false;
        }
        if (isMiningReward()) {
            return receiver != null && !receiver.isBlank() && amount > 0 && fee == 0 && hasCanonicalOutputs();
        }
        if (sender == null || sender.isBlank() || receiver == null || receiver.isBlank()) {
            return false;
        }
        if (amount <= 0 || fee < 0 || signature == null || signature.isBlank()) {
            return false;
        }
        if (getInputs().isEmpty()) {
            return false;
        }
        if (!hasCanonicalOutputs()) {
            return false;
        }
        return CryptoUtil.verify(sender, getSigningPayload(), signature);
    }

    public boolean isMiningReward() {
        return SYSTEM_SENDER.equals(sender);
    }

    private String calculateTransactionId() {
        return StringUtil.applySha256(getSigningPayload());
    }

    public String getSigningPayload() {
        StringBuilder payload = new StringBuilder();
        payload.append("sender=").append(sender).append('\n');
        payload.append("receiver=").append(receiver).append('\n');
        payload.append("amount=").append(canonicalAmount(amount)).append('\n');
        payload.append("fee=").append(canonicalAmount(fee)).append('\n');
        payload.append("timeStamp=").append(timeStamp).append('\n');
        payload.append("nonce=").append(nonce).append('\n');
        payload.append("inputs=").append(getInputs().size()).append('\n');
        for (TransactionInput input : getInputs()) {
            payload.append(input.transactionId()).append(':').append(input.outputIndex()).append('\n');
        }
        payload.append("outputs=").append(getOutputs().size()).append('\n');
        for (TransactionOutput output : getOutputs()) {
            payload.append(output.receiver()).append(':').append(canonicalAmount(output.amount())).append('\n');
        }
        return payload.toString();
    }

    private boolean hasCanonicalOutputs() {
        if (getOutputs().isEmpty()) {
            return false;
        }
        TransactionOutput paymentOutput = getOutputs().getFirst();
        return receiver.equals(paymentOutput.receiver())
                && MoneyUnits.equals(paymentOutput.amount(), amount)
                && getOutputs().stream().allMatch(output ->
                output.receiver() != null
                        && !output.receiver().isBlank()
                        && Double.isFinite(output.amount())
                        && output.amount() > 0
        );
    }

    private static String canonicalAmount(double value) {
        return MoneyUnits.canonical(value);
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

    public long getAmountUnits() {
        return MoneyUnits.toUnits(amount);
    }

    public double getFee() {
        return fee;
    }

    public long getFeeUnits() {
        return MoneyUnits.toUnits(fee);
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

    public String getNonce() {
        return nonce;
    }

    public List<TransactionInput> getInputs() {
        return inputs == null ? List.of() : List.copyOf(inputs);
    }

    public List<TransactionOutput> getOutputs() {
        return outputs == null ? List.of(new TransactionOutput(receiver, amount)) : List.copyOf(outputs);
    }
}
