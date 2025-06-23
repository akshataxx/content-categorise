package com.app.categorise.domain.model;

public class Category {
    private String id;
    private String name;
    // getters and setters

    // business logic methods
    public boolean isValid() {
        return name != null && !name.isEmpty();
    }
}
