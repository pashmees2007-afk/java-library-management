package com.library.model;

/** A title and how many times it has been issued. */
public record PopularBook(String title, String author, int timesIssued) {
}
