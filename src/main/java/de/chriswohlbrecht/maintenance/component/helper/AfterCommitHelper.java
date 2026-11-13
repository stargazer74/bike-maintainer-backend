package de.chriswohlbrecht.maintenance.component.helper;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Side effects that must only happen if the data they report on was really saved, e.g. emails: a transaction that is
 * rolled back (a concurrent change, a failure) sends nothing, and its retry sends once.
 */
public final class AfterCommitHelper {

    private AfterCommitHelper() {
    }

    /** Runs the action after the current transaction committed; without a transaction right away. */
    public static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
