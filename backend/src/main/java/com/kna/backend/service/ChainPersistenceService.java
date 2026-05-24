package com.kna.backend.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.kna.backend.dto.PersistedPeer;
import com.kna.backend.entity.Block;
import com.kna.backend.entity.Transaction;
import com.kna.backend.entity.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Service
public class ChainPersistenceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChainPersistenceService.class);
    private static final Type BLOCK_LIST_TYPE = new TypeToken<List<Block>>() {
    }.getType();

    private final Gson gson = new Gson();
    private final boolean enabled;
    private final String type;
    private final Path filePath;
    private final JdbcTemplate jdbcTemplate;

    public ChainPersistenceService(
            @Value("${blockchain.persistence.enabled:false}") boolean enabled,
            @Value("${blockchain.persistence.type:file}") String type,
            @Value("${blockchain.persistence.file:data/blockchain-chain.json}") String filePath,
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider
    ) {
        this.enabled = enabled;
        this.type = type;
        this.filePath = Path.of(filePath);
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    public Optional<List<Block>> loadChain() {
        if (!enabled) {
            return Optional.empty();
        }

        if ("database".equalsIgnoreCase(type)) {
            return loadFromDatabase();
        }

        return loadFromFile();
    }

    public void saveChain(List<Block> chain) {
        if (!enabled) {
            return;
        }

        if ("database".equalsIgnoreCase(type)) {
            saveToDatabase(chain);
            return;
        }

        saveToFile(chain);
    }

    public Optional<List<Transaction>> loadPendingTransactions() {
        if (!enabled || !"database".equalsIgnoreCase(type) || jdbcTemplate == null) {
            return Optional.empty();
        }

        try {
            ensureSchema();
            List<String> rows = jdbcTemplate.query(
                    "select transaction_json from blockchain_transactions where pending = true order by transaction_order",
                    (resultSet, rowNumber) -> resultSet.getString("transaction_json")
            );
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(rows.stream()
                    .map(row -> gson.fromJson(row, Transaction.class))
                    .toList());
        } catch (Exception exception) {
            LOGGER.warn("Could not load pending transactions from database", exception);
            return Optional.empty();
        }
    }

    public void savePendingTransactions(List<Transaction> transactions) {
        if (!enabled || !"database".equalsIgnoreCase(type) || jdbcTemplate == null) {
            return;
        }

        try {
            ensureSchema();
            jdbcTemplate.update("delete from blockchain_transactions where pending = true");
            for (int i = 0; i < transactions.size(); i++) {
                saveTransaction(transactions.get(i), null, i, true);
            }
        } catch (Exception exception) {
            LOGGER.warn("Could not save pending transactions to database", exception);
        }
    }

    public void saveWallet(Wallet wallet) {
        if (!enabled || !"database".equalsIgnoreCase(type) || jdbcTemplate == null) {
            return;
        }

        try {
            ensureSchema();
            jdbcTemplate.update(
                    """
                            merge into blockchain_wallets (public_key, private_key, created_at)
                            key (public_key)
                            values (?, ?, current_timestamp)
                            """,
                    wallet.publicKey(),
                    wallet.privateKey()
            );
        } catch (Exception exception) {
            LOGGER.warn("Could not save wallet to database", exception);
        }
    }

    public Optional<List<PersistedPeer>> loadPeers() {
        if (!enabled || !"database".equalsIgnoreCase(type) || jdbcTemplate == null) {
            return Optional.empty();
        }

        try {
            ensureSchema();
            List<PersistedPeer> peers = jdbcTemplate.query(
                    "select peer_id, base_url from blockchain_peers order by peer_id",
                    (resultSet, rowNumber) -> new PersistedPeer(
                            resultSet.getString("peer_id"),
                            resultSet.getString("base_url")
                    )
            );
            return peers.isEmpty() ? Optional.empty() : Optional.of(peers);
        } catch (Exception exception) {
            LOGGER.warn("Could not load peers from database", exception);
            return Optional.empty();
        }
    }

    public void savePeers(List<PersistedPeer> peers) {
        if (!enabled || !"database".equalsIgnoreCase(type) || jdbcTemplate == null) {
            return;
        }

        try {
            ensureSchema();
            jdbcTemplate.update("delete from blockchain_peers");
            for (PersistedPeer peer : peers) {
                jdbcTemplate.update(
                        """
                                insert into blockchain_peers (peer_id, base_url, mode, created_at)
                                values (?, ?, ?, current_timestamp)
                                """,
                        peer.peerId(),
                        peer.baseUrl(),
                        peer.baseUrl() == null ? "simulated" : "http"
                );
            }
        } catch (Exception exception) {
            LOGGER.warn("Could not save peers to database", exception);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getType() {
        return type;
    }

    private Optional<List<Block>> loadFromFile() {
        if (!Files.exists(filePath)) {
            return Optional.empty();
        }

        try (Reader reader = Files.newBufferedReader(filePath)) {
            List<Block> chain = gson.fromJson(reader, BLOCK_LIST_TYPE);
            if (chain == null || chain.isEmpty()) {
                return Optional.empty();
            }
            LOGGER.info("Loaded persisted blockchain from {}", filePath);
            return Optional.of(chain);
        } catch (Exception exception) {
            LOGGER.warn("Could not load persisted blockchain from {}", filePath, exception);
            return Optional.empty();
        }
    }

    private void saveToFile(List<Block> chain) {
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(filePath)) {
                gson.toJson(chain, BLOCK_LIST_TYPE, writer);
            }
            LOGGER.info("Saved blockchain to {}", filePath);
        } catch (Exception exception) {
            LOGGER.warn("Could not save blockchain to {}", filePath, exception);
        }
    }

    private Optional<List<Block>> loadFromDatabase() {
        if (jdbcTemplate == null) {
            LOGGER.warn("Database persistence requested, but JdbcTemplate is not available");
            return Optional.empty();
        }

        try {
            ensureSchema();
            List<String> rows = jdbcTemplate.query(
                    "select block_json from blockchain_blocks order by block_index",
                    (resultSet, rowNumber) -> resultSet.getString("block_json")
            );
            if (rows.isEmpty()) {
                return Optional.empty();
            }

            List<Block> chain = rows.stream()
                    .map(row -> gson.fromJson(row, Block.class))
                    .toList();
            if (chain == null || chain.isEmpty()) {
                return Optional.empty();
            }
            LOGGER.info("Loaded persisted blockchain from database");
            return Optional.of(chain);
        } catch (Exception exception) {
            LOGGER.warn("Could not load persisted blockchain from database", exception);
            return Optional.empty();
        }
    }

    private void saveToDatabase(List<Block> chain) {
        if (jdbcTemplate == null) {
            LOGGER.warn("Database persistence requested, but JdbcTemplate is not available");
            return;
        }

        try {
            ensureSchema();
            jdbcTemplate.update("delete from blockchain_transactions where pending = false");
            jdbcTemplate.update("delete from blockchain_blocks");
            for (Block block : chain) {
                jdbcTemplate.update(
                        """
                                insert into blockchain_blocks (
                                    block_index, hash, previous_hash, nonce, time_stamp, tamper_marker, block_json
                                ) values (?, ?, ?, ?, ?, ?, ?)
                                """,
                        block.getIndex(),
                        block.getHash(),
                        block.getPreviousHash(),
                        block.getNonce(),
                        block.getTimeStamp(),
                        block.getTamperMarker(),
                        gson.toJson(block)
                );
                List<Transaction> transactions = block.getTransactions();
                for (int i = 0; i < transactions.size(); i++) {
                    saveTransaction(transactions.get(i), block.getIndex(), i, false);
                }
            }
            LOGGER.info("Saved blockchain to database");
        } catch (Exception exception) {
            LOGGER.warn("Could not save blockchain to database", exception);
        }
    }

    private void saveTransaction(Transaction transaction, Integer blockIndex, int transactionOrder, boolean pending) {
        jdbcTemplate.update(
                """
                        merge into blockchain_transactions (
                            transaction_id, block_index, transaction_order, sender, receiver, amount, fee,
                            time_stamp, signature, pending, transaction_json
                        )
                        key (transaction_id)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                transaction.getTransactionId(),
                blockIndex,
                transactionOrder,
                transaction.getSender(),
                transaction.getReceiver(),
                transaction.getAmount(),
                transaction.getFee(),
                transaction.getTimeStamp(),
                transaction.getSignature(),
                pending,
                gson.toJson(transaction)
        );
    }

    private void ensureSchema() {
        jdbcTemplate.execute("""
                create table if not exists blockchain_schema_migrations (
                    version int primary key,
                    description varchar(255) not null,
                    applied_at timestamp not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists blockchain_state (
                    state_key varchar(64) primary key,
                    state_json clob not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists blockchain_blocks (
                    block_index int primary key,
                    hash varchar(128) not null,
                    previous_hash varchar(128) not null,
                    nonce int not null,
                    time_stamp bigint not null,
                    tamper_marker clob,
                    block_json clob not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists blockchain_transactions (
                    transaction_id varchar(128) primary key,
                    block_index int,
                    transaction_order int not null,
                    sender clob not null,
                    receiver clob not null,
                    amount double not null,
                    fee double not null,
                    time_stamp bigint not null,
                    signature clob,
                    pending boolean not null,
                    transaction_json clob not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists blockchain_wallets (
                    public_key varchar(4096) primary key,
                    private_key clob not null,
                    created_at timestamp not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists blockchain_peers (
                    peer_id varchar(128) primary key,
                    base_url varchar(512),
                    mode varchar(32) not null,
                    created_at timestamp not null
                )
                """);
        jdbcTemplate.update(
                """
                        merge into blockchain_schema_migrations (version, description, applied_at)
                        key (version)
                        values (1, 'phase-4-normalized-state', current_timestamp)
                        """
        );
    }
}
