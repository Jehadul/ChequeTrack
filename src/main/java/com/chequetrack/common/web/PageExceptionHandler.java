package com.chequetrack.common.web;

import com.chequetrack.common.exception.BusinessException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/**
 * Fallback for Thymeleaf pages. Controllers should normally catch
 * {@link BusinessException} and show it on the form; anything that escapes
 * lands here. 404 and 500 are rendered by Spring Boot from templates/error/.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@ControllerAdvice(annotations = Controller.class)
public class PageExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ModelAndView handleBusiness(BusinessException ex) {
        return errorPage(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ModelAndView handleConflict() {
        return errorPage(HttpStatus.CONFLICT,
                "This record was changed by another user. Go back, reload and try again.");
    }

    private static ModelAndView errorPage(HttpStatus status, String message) {
        ModelAndView mav = new ModelAndView("error/general");
        mav.setStatus(status);
        mav.addObject("status", status.value());
        mav.addObject("title", status.getReasonPhrase());
        mav.addObject("message", message);
        return mav;
    }
}
