package org.paron.syncservice.exception;

import lombok.Data;

@Data
public class SyncException extends RuntimeException{

    private final String errorCode;

    public SyncException(String errorCode,String message){
        super(message);
        this.errorCode=errorCode;
    }

}
