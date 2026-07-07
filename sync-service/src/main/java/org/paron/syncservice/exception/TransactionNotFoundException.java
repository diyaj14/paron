package org.paron.syncservice.exception;

public class TransactionNotFoundException extends SyncException{

    public TransactionNotFoundException(String id){
        super("Transaction_Not_Found","No offline transaction found with id: " + id);
    }
}
