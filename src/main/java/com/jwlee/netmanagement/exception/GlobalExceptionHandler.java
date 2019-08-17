package com.jwlee.netmanagement.exception;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author jianwuli
 * @time 2019/8/17 16:29
 */
@ControllerAdvice
@ResponseBody
public class GlobalExceptionHandler {

    @InitBinder
    public void initBinder(WebDataBinder webDataBinder) {

    }

    /*@ExceptionHandler(CusException.class)*/
}
