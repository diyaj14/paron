package org.paron.syncservice.exception;

public class SyncException extends RuntimeException{

    private final String errorCode;

    public SyncException(String errorCode,String message){
        super(message);
        this.errorCode=errorCode;
    }

    public String getErrorCode(){
        return errorCode;
    }

}
