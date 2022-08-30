package com.tigerbeetle;

import java.util.StringJoiner;
import java.util.UUID;

public final class Client implements AutoCloseable {
    static {
        System.loadLibrary("tb_jniclient");
    }

    private static final int DEFAULT_MAX_CONCURRENCY = 32;

    private final int clusterID;
    private long clientHandle;
    private long packetsHead;
    private long packetsTail;

    public Client(int clusterID, String[] addresses, int maxConcurrency) throws IllegalArgumentException, Exception {
        if (clusterID < 0)
            throw new IllegalArgumentException("clusterID must be positive");
        if (addresses == null || addresses.length == 0)
            throw new IllegalArgumentException("Invalid addresses");

        // Cap the maximum amount of packets
        if (maxConcurrency <= 0)
            throw new IllegalArgumentException("Invalid maxConcurrency");
        if (maxConcurrency > 4096)
            maxConcurrency = 4096;

        var joiner = new StringJoiner(",");
        for (var address : addresses) {
            joiner.add(address);
        }

        this.clusterID = clusterID;
        int status = clientInit(clusterID, joiner.toString(), maxConcurrency);
        if (status != 0)
            throw new Exception("result " + status);
    }

    @Override
    public void close() throws Exception {
        if (clientHandle != 0) {
            clientDeinit();
            clientHandle = 0;
            packetsHead = 0;
            packetsTail = 0;
        }
    }

    private native int clientInit(int clusterID, String addresses, int maxConcurrency);

    private native void clientDeinit();

    private native void submit(Request request);

    public CreateAccountResult CreateAccount(Account account) throws InterruptedException, Exception {
        var batch = new AccountsBatch(1);
        batch.Add(account);

        CreateAccountsResult[] results = CreateAccounts(batch);
        if (results.length == 0) {
            return CreateAccountResult.Ok;
        } else {
            return results[0].result;
        }
    }

    public CreateAccountsResult[] CreateAccounts(Account[] batch) throws InterruptedException, Exception {
        return CreateAccounts(new AccountsBatch(batch));
    }

    public CreateAccountsResult[] CreateAccounts(AccountsBatch batch) throws InterruptedException, Exception {
        var request = new Request(this, Request.Operations.CREATE_ACCOUNTS, batch);

        submit(request);

        return (CreateAccountsResult[]) request.waitForResult();

    }

    public Account LookupAccount(UUID uuid) throws InterruptedException, Exception {
        var batch = new UUIDsBatch(1);
        batch.Add(uuid);

        Account[] results = LookupAccounts(batch);
        if (results.length == 0) {
            return null;
        } else {
            return results[0];
        }
    }

    public Account[] LookupAccounts(UUID[] batch) throws InterruptedException, Exception {
        return LookupAccounts(new UUIDsBatch(batch));
    }

    public Account[] LookupAccounts(UUIDsBatch batch) throws InterruptedException, Exception {
        var request = new Request(this, Request.Operations.LOOKUP_ACCOUNTS, batch);

        submit(request);

        return (Account[]) request.waitForResult();
    }

    public static void main(String[] args) {
        try (var client = new Client(0, new String[] { "127.0.0.1:3001" }, 32)) {

            var batch = new AccountsBatch(10);

            var id1 = UUID.randomUUID();
            var id2 = UUID.randomUUID();

            var account1 = new Account();
            account1.setId(id1);
            account1.setCode(100);
            account1.setLedger(720);
            batch.Add(account1);

            var account2 = new Account();
            account2.setId(id2);
            account2.setCode(200);
            account2.setLedger(720);
            batch.Add(account2);

            var results = client.CreateAccounts(batch);
            for (var result : results) {
                System.out.printf("Index=%d Value=%s\n", result.index, result.result);
            }

            var accounts = client.LookupAccounts(new UUID[] { id1, id2 });

            for (var account : accounts) {
                System.out.printf("ID=%s Code=%d Ledger=%d\n", account.getId(), account.getCode(), account.getLedger());
            }

            System.console().readLine();

        } catch (Exception e) {
            System.out.println("Big bad Zig error handled in Java >:(");
            e.printStackTrace();
        }
    }

}