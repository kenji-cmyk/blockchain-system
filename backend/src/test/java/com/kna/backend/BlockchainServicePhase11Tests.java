package com.kna.backend;

import com.kna.backend.entity.Block;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BlockchainServicePhase11Tests {

    private static final int DIFFICULTY = 0;
    private static final double MINING_REWARD = 10;
    private static final int MAX_TRANSACTIONS_PER_BLOCK = 5;

    @TempDir
    private Path tempDir;

    @Test
    void deductsPendingOutgoingAmountFromAvailableBalanceAndRestoresAfterMining() {
        BlockchainService service = newService(false, tempDir.resolve("unused.json"));
        Wallet sender = service.createWallet();
        Wallet receiver = service.createWallet();
        Wallet miner = service.createWallet();

        service.addBlock(sender.publicKey());
        Transaction pending = service.createTransaction(sender.publicKey(), receiver.publicKey(), 4, 0.5, sender.privateKey());

        assertThat(pending.getInputs()).isNotEmpty();
        assertThat(pending.getOutputs()).hasSize(2);
        assertThat(service.getBalance(sender.publicKey())).isEqualTo(10);
        assertThat(service.getAvailableBalance(sender.publicKey())).isEqualTo(5.5);
        assertThat(service.getPendingTransactions()).hasSize(1);

        Block minedBlock = service.minePendingTransactions(miner.publicKey());

        assertThat(minedBlock.getTransactions()).hasSize(2);
        assertThat(service.getPendingTransactions()).isEmpty();
        assertThat(service.getBalance(sender.publicKey())).isEqualTo(5.5);
        assertThat(service.getBalance(receiver.publicKey())).isEqualTo(4);
        assertThat(service.getBalance(miner.publicKey())).isEqualTo(10.5);
    }

    @Test
    void tracksRejectedBroadcastBlocksAsOrphansWhenParentIsUnknown() {
        BlockchainService service = newService(false, tempDir.resolve("unused.json"));
        Block orphan = new Block(
                service.getBlocks().size(),
                List.of(Transaction.miningReward("orphan-miner", MINING_REWARD)),
                "missing-parent-hash"
        );
        orphan.mineBlock(DIFFICULTY);

        boolean accepted = service.acceptBroadcastBlock(orphan);

        assertThat(accepted).isFalse();
        assertThat(service.getForkBlocks()).isEmpty();
        assertThat(service.getOrphanBlocks())
                .singleElement()
                .satisfies(reference -> {
                    assertThat(reference.index()).isEqualTo(orphan.getIndex());
                    assertThat(reference.hash()).isEqualTo(orphan.getHash());
                    assertThat(reference.previousHash()).isEqualTo("missing-parent-hash");
                });
    }

    @Test
    void restoresValidFilePersistedChainOnStartup() {
        Path chainFile = tempDir.resolve("chain.json");
        BlockchainService firstService = newService(true, chainFile);
        Wallet miner = firstService.createWallet();
        firstService.addBlock(miner.publicKey());

        BlockchainService restoredService = newService(true, chainFile);

        assertThat(restoredService.getBlocks()).hasSize(2);
        assertThat(restoredService.getBlocks().get(1).getHash()).isEqualTo(firstService.getBlocks().get(1).getHash());
        assertThat(restoredService.isValid()).isTrue();
    }

    private BlockchainService newService(boolean persistenceEnabled, Path chainFile) {
        OperationalMetricsService metricsService = new OperationalMetricsService();
        ChainPersistenceService persistenceService = new ChainPersistenceService(
                persistenceEnabled,
                "file",
                chainFile.toString(),
                new DefaultListableBeanFactory().getBeanProvider(JdbcTemplate.class)
        );
        return new BlockchainService(
                DIFFICULTY,
                MINING_REWARD,
                MAX_TRANSACTIONS_PER_BLOCK,
                1000,
                "cumulative-difficulty",
                0,
                persistenceService,
                metricsService
        );
    }
}
