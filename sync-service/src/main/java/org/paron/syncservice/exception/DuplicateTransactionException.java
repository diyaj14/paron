package org.paron.syncservice.exception;

public class DuplicateTransactionException extends SyncException{
    public DuplicateTransactionException(String deviceTransactionId){
        super("DUPLICATE_TRANSACTION","Transaction " + deviceTransactionId + " has already been processed");

    }
}
