package com.app.categorise.data;

import com.app.categorise.data.entity.CategoryEntity;
import com.app.categorise.data.repository.CategoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds the database with a predefined set of special categories on application startup.
 * This runner checks if any categories exist and, if not, populates the database to ensure
 * the application has its required base data.
 */
@Component
public class CategorySeeder implements CommandLineRunner {

    private final CategoryRepository categoryRepository;

    public CategorySeeder(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        if (categoryRepository.count() == 0) {
            seedCategories();
        }
    }

    private void seedCategories() {
        List<CategoryEntity> categories = List.of(
                new CategoryEntity("Recipe", "Curated list of delicious recipes"),
                new CategoryEntity("DIY", "Do-it-yourself projects and crafts"),
                new CategoryEntity("Cleaning", "Cleaning tips and hacks")
        );
        categoryRepository.saveAll(categories);
        System.out.println("Seeded " + categories.size() + " categories into the database.");
    }
} 