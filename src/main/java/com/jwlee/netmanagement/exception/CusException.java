package com.jwlee.netmanagement.exception;

import com.jwlee.netmanagement.common.constant.EErrorCode;

/**
 * @author jianwuli
 * @time 2019/8/17 16:20
 */
public class CusException extends RuntimeException{

    private static final long serialVersionUID = 1L;

    private String errorMSG;

    private String errorCode;

    public CusException() {
        super();
    }

    public CusException(String arg0) {
        super(arg0);
        this.errorMSG = arg0;
    }

    public CusException(Throwable arg0) {
        super(arg0);
    }

    public CusException(String arg0, Throwable arg1) {
        super(arg0, arg1);
    }

    public CusException(String code, String message) {
        this.errorCode = code;
        this.errorMSG = message;
    }

    public CusException(EErrorCode code) {
        this.errorCode = code.getCode();
        this.errorMSG = code.getMessage();
    }

    public String getErrorMSG() {
        return errorMSG;
    }

    public void setErrorMSG(String errorMSG) {
        this.errorMSG = errorMSG;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
}
