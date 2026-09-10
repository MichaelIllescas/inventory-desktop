package com.ferreteria.repositories;

import com.ferreteria.models.Quote;

import java.util.List;
import java.util.Optional;

public interface QuoteRepository {
    Quote save(Quote quote);
    Quote update(Quote quote);
    void delete(int id);
    List<Quote> findAll();
    Optional<Quote> findById(int id);
}
