package com.jxxerick.expensesplit.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException group(Long id) {
        return new ResourceNotFoundException("Grupo %d não encontrado".formatted(id));
    }

    public static ResourceNotFoundException member(Long id) {
        return new ResourceNotFoundException("Membro %d não pertence ao grupo".formatted(id));
    }
}
