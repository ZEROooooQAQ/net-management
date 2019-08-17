package com.jwlee.netmanagement.common.constant;

/**
 * @author jianwuli
 * @time 2019/8/17 16:25
 */
public enum EErrorCode {
    // todo need to add error desc
    ;

    private String code;
    private String message;

    private EErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
