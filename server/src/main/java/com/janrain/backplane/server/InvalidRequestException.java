package com.janrain.backplane.server;

import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.validation.ConstraintViolation;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@ResponseStatus(value=HttpStatus.BAD_REQUEST)
public class InvalidRequestException extends RuntimeException {
	
	private static final long serialVersionUID = 1L;
	private Map<String, String> fieldErrors = new HashMap<String, String>();
	boolean hasMessageFlag = false;

	public InvalidRequestException() {
		super();
	}
	
	public InvalidRequestException(String message) {
		super(message);
		hasMessageFlag = true;
	}
	
	public InvalidRequestException(BindingResult result) {

        for (FieldError error : result.getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
	}
	
	public <T> InvalidRequestException(Set<ConstraintViolation<T>> failures, Class<?> T) {
		for (ConstraintViolation<T> failure : failures) {
			fieldErrors.put(failure.getPropertyPath().toString(), failure.getMessage());
		}
	}	
	
	public Map<String, String> getFieldErrors() {
		return fieldErrors;
	}
	
	public boolean hasErrors() {
		return hasFieldErrors();
	}
	
	public boolean hasFieldErrors() {
		return !fieldErrors.isEmpty();
	}
	
	public boolean hasMessage() {
		return hasMessageFlag;
	}
	
	public String toString() {
		StringBuilder out = new StringBuilder();
		if (hasMessage()) {
			out.append(getMessage());
		}
		if (hasFieldErrors()) {
			for (String field : fieldErrors.keySet()) {
                out.append(field).append(": ").append(fieldErrors.get(field));
			}
		}
		return out.toString();
	}
	
}
