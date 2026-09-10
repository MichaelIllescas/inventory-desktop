package com.ferreteria.services;

import com.ferreteria.models.Quote;
import com.ferreteria.models.QuoteLineItem;
import com.ferreteria.repositories.QuoteRepository;
import com.ferreteria.util.AppLogger;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class QuoteService {

    private final QuoteRepository repository;

    public QuoteService(QuoteRepository repository) {
        this.repository = repository;
    }

    public List<Quote> getAllQuotes() {
        return repository.findAll();
    }

    public Optional<Quote> findById(int id) {
        return repository.findById(id);
    }

    public Quote createQuote(Quote quote) {
        validate(quote);
        if (quote.getDate() == null || quote.getDate().isBlank()) {
            quote.setDate(LocalDateTime.now().toString());
        }
        quote.setTotal(calculateTotal(quote.getItems()));
        Quote saved = repository.save(quote);
        AppLogger.info("QuoteService", "createQuote",
                "Presupuesto creado N° " + saved.getId() + " por " + saved.getTotal());
        return saved;
    }

    public Quote updateQuote(Quote quote) {
        if (quote.getId() == null) {
            throw new IllegalArgumentException("El presupuesto no tiene id.");
        }
        validate(quote);
        quote.setTotal(calculateTotal(quote.getItems()));
        Quote updated = repository.update(quote);
        AppLogger.info("QuoteService", "updateQuote",
                "Presupuesto actualizado N° " + updated.getId() + " por " + updated.getTotal());
        return updated;
    }

    private void validate(Quote quote) {
        if (quote.getItems() == null || quote.getItems().isEmpty()) {
            throw new IllegalArgumentException("El presupuesto debe tener al menos un item.");
        }
        for (QuoteLineItem item : quote.getItems()) {
            if (item.getDescription() == null || item.getDescription().isBlank()) {
                throw new IllegalArgumentException("Todos los items deben tener descripcion.");
            }
            if (item.getQuantity() <= 0) {
                throw new IllegalArgumentException("La cantidad debe ser mayor a cero en \"" + item.getDescription() + "\".");
            }
            // Un precio negativo es valido a proposito: asi se cargan los descuentos.
        }
        if (calculateTotal(quote.getItems()) < 0) {
            throw new IllegalArgumentException("El total del presupuesto no puede ser negativo. Revise los descuentos.");
        }
    }

    public void deleteQuote(int id) {
        repository.delete(id);
        AppLogger.info("QuoteService", "deleteQuote", "Presupuesto eliminado N° " + id);
    }

    public double calculateTotal(List<QuoteLineItem> items) {
        return items.stream().mapToDouble(QuoteLineItem::getSubtotal).sum();
    }
}
