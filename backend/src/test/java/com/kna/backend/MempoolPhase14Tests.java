package com.kna.backend;

import com.kna.backend.entity.Transaction;
import com.kna.backend.entity.Wallet;
import com.kna.backend.service.BlockchainService;
import com.kna.backend.service.ChainPersistenceService;
import com.kna.backend.service.OperationalMetricsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MempoolPhase14Tests {

    private static final int DIFFICULTY = 0;
    private static final double MINING_REWARD = 10;
    private static final int MAX_TRANSACTIONS_PER_BLOCK = 5;
    private static final int MAX_PENDING_TRANSACTIONS = 1;

    @TempDir
    private Path tempDir;

    @Test
    void evictsLowestFeeTransactionWhenBoundedMempoolIsFull() {
        BlockchainService service = newService();
        Wallet lowFeeSender = service.createWallet();
        Wallet highFeeSender = service.createWallet();
        Wallet rejectedSender = service.createWallet();
        Wallet receiver = service.createWallet();
        service.addBlock(lowFeeSender.publicKey());
        service.addBlock(highFeeSender.publicKey());
        service.addBlock(rejectedSender.publicKey());

        Transaction lowFee = service.createTransaction(
                lowFeeSender.publicKey(),
                receiver.publicKey(),
                1,
                0.01,
                lowFeeSender.privateKey()
        );
        Transaction highFee = service.createTransaction(
                highFeeSender.publicKey(),
                receiver.publicKey(),
                1,
                0.50,
                highFeeSender.privateKey()
        );

        assertThat(service.getPendingTransactions())
                .extracting(Transaction::getTransactionId)
                .containsExactly(highFee.getTransactionId());
        assertThat(service.getPendingTransactions())
                .extracting(Transaction::getTransactionId)
                .doesNotContain(lowFee.getTransactionId());

        assertThatThrownBy(() -> service.createTransaction(
                rejectedSender.publicKey(),
                receiver.publicKey(),
                1,
                0.01,
                rejectedSender.privateKey()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mempool is full")
                .hasMessageContaining("fee");

        assertThat(service.getPendingTransactions())
                .extracting(Transaction::getTransactionId)
                .containsExactly(highFee.getTransactionId());
    }

    private BlockchainService newService() {
        OperationalMetricsService metricsService = new OperationalMetricsService();
        ChainPersistenceService persistenceService = new ChainPersistenceService(
                false,
                "file",
                tempDir.resolve("unused.json").toString(),
                new DefaultListableBeanFactory().getBeanProvider(JdbcTemplate.class)
        );
        return new BlockchainService(
                DIFFICULTY,
                MINING_REWARD,
                MAX_TRANSACTIONS_PER_BLOCK,
                MAX_PENDING_TRANSACTIONS,
                "cumulative-difficulty",
                0,
                persistenceService,
                metricsService
        );
    }
}
