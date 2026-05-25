package com.kna.backend;

import com.kna.backend.entity.Block;
import com.kna.backend.entity.Transaction;
import com.kna.backend.entity.TransactionInput;
import com.kna.backend.entity.TransactionOutput;
import com.kna.backend.entity.Wallet;
import com.kna.backend.pkg.utils.CryptoUtil;
import com.kna.backend.pkg.validate.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerValidationTests {

    private static final int DIFFICULTY = 0;
    private static final double MINING_REWARD = 10;

    @Test
    void validatesUtxoDependenciesWithinSameBlock() {
        Wallet sender = CryptoUtil.generateWallet();
        Wallet receiver = CryptoUtil.generateWallet();
        Wallet secondReceiver = CryptoUtil.generateWallet();
        Wallet miner = CryptoUtil.generateWallet();

        Block genesis = minedBlock(0, List.of(Transaction.miningReward(sender.publicKey(), MINING_REWARD)), "0");
        Transaction first = signedSpend(
                sender,
                receiver.publicKey(),
                3,
                0.5,
                List.of(new TransactionInput(genesis.getTransactions().getFirst().getTransactionId(), 0)),
                List.of(
                        new TransactionOutput(receiver.publicKey(), 3),
                        new TransactionOutput(sender.publicKey(), 6.5)
                )
        );
        Transaction second = signedSpend(
                sender,
                secondReceiver.publicKey(),
                2,
                0.25,
                List.of(new TransactionInput(first.getTransactionId(), 1)),
                List.of(
                        new TransactionOutput(secondReceiver.publicKey(), 2),
                        new TransactionOutput(sender.publicKey(), 4.25)
                )
        );
        Block block = minedBlock(
                1,
                List.of(first, second, Transaction.miningReward(miner.publicKey(), 10.75)),
                genesis.getHash()
        );

        assertThat(Validator.isChainValid(List.of(genesis, block), DIFFICULTY, 5, MINING_REWARD)).isTrue();
    }

    @Test
    void rejectsDoubleSpendOfSameOutputInsideBlock() {
        Wallet sender = CryptoUtil.generateWallet();
        Wallet firstReceiver = CryptoUtil.generateWallet();
        Wallet secondReceiver = CryptoUtil.generateWallet();

        Block genesis = minedBlock(0, List.of(Transaction.miningReward(sender.publicKey(), MINING_REWARD)), "0");
        TransactionInput fundingOutput = new TransactionInput(genesis.getTransactions().getFirst().getTransactionId(), 0);
        Transaction first = signedSpend(
                sender,
                firstReceiver.publicKey(),
                6,
                0,
                List.of(fundingOutput),
                List.of(
                        new TransactionOutput(firstReceiver.publicKey(), 6),
                        new TransactionOutput(sender.publicKey(), 4)
                )
        );
        Transaction second = signedSpend(
                sender,
                secondReceiver.publicKey(),
                5,
                0,
                List.of(fundingOutput),
                List.of(
                        new TransactionOutput(secondReceiver.publicKey(), 5),
                        new TransactionOutput(sender.publicKey(), 5)
                )
        );
        Block block = minedBlock(
                1,
                List.of(first, second, Transaction.miningReward(sender.publicKey(), MINING_REWARD)),
                genesis.getHash()
        );

        assertThat(Validator.isChainValid(List.of(genesis, block), DIFFICULTY, 5, MINING_REWARD)).isFalse();
    }

    @Test
    void rejectsInvalidOutputAmountAndImplicitReplay() {
        Wallet sender = CryptoUtil.generateWallet();
        Wallet receiver = CryptoUtil.generateWallet();

        Block genesis = minedBlock(0, List.of(Transaction.miningReward(sender.publicKey(), MINING_REWARD)), "0");
        Transaction transaction = signedSpend(
                sender,
                receiver.publicKey(),
                4,
                0,
                List.of(new TransactionInput(genesis.getTransactions().getFirst().getTransactionId(), 0)),
                List.of(new TransactionOutput(receiver.publicKey(), 5))
        );
        Block block = minedBlock(1, List.of(transaction, Transaction.miningReward(sender.publicKey(), MINING_REWARD)), genesis.getHash());

        assertThat(transaction.isValid()).isFalse();
        assertThat(Validator.isChainValid(List.of(genesis, block), DIFFICULTY, 5, MINING_REWARD)).isFalse();
    }

    private Transaction signedSpend(
            Wallet sender,
            String receiver,
            double amount,
            double fee,
            List<TransactionInput> inputs,
            List<TransactionOutput> outputs
    ) {
        Transaction transaction = new Transaction(sender.publicKey(), receiver, amount, fee, inputs, outputs);
        transaction.sign(sender.privateKey());
        return transaction;
    }

    private Block minedBlock(int index, List<Transaction> transactions, String previousHash) {
        Block block = new Block(index, transactions, previousHash);
        block.mineBlock(DIFFICULTY);
        return block;
    }
}
