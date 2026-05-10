package com.llmmemory.shared.exception;

public class DuplicateTitleException extends RuntimeException {

    public DuplicateTitleException(String title) {
        super("A conversation with this title already exists: " + title);
    }
}
