package com.ferreteria.repositories;

import com.ferreteria.models.Expense;

import java.util.List;

public interface ExpenseRepository {
    Expense save(Expense expense);
    void delete(int id);
    List<Expense> findInRange(String dateTimeFrom, String dateTimeTo);
    double getTotalInRange(String dateTimeFrom, String dateTimeTo);
}
