package com.app.categorise.data;

import com.app.categorise.data.entity.CategoryEntity;
import com.app.categorise.data.repository.CategoryRepository;
import com.app.categorise.domain.service.CategoryService;
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

    private final CategoryService categoryService;

    public CategorySeeder(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Override
    public void run(String... args) throws Exception {
        seedCategories();
    }

    private void seedCategories() {
        List<CategoryEntity> categories = List.of(
            new CategoryEntity( "Recipes", "Curated list of delicious recipes"),
            new CategoryEntity("DIY", "Do-it-yourself projects and crafts"),
            new CategoryEntity("Cleaning", "Cleaning tips and hacks"),
            new CategoryEntity( "Sport", "Sports news and updates"),
            new CategoryEntity("Coding", "Building...."),
            new CategoryEntity("Tech", "Nerrrrd"),
            new CategoryEntity("Movies", "All things movies"),
            new CategoryEntity("TV", "All things TV"),
            new CategoryEntity("Music", "All things music"),
            new CategoryEntity("Dance", "Moving to the beat"),
            new CategoryEntity("Comedy", "Haha funny"),
            new CategoryEntity("Investing", "Knowledge to help you make smart investments"),
            new CategoryEntity("Travel", "Planning your next trip"),
            new CategoryEntity("Lifestyle", "General lifestyle tips"),
            new CategoryEntity("Workouts", "Workout routines and tips"),
            new CategoryEntity("Diets", "Eat right or not..."),
            new CategoryEntity("Health", "Keeping you healthy"),
            new CategoryEntity("Makeup", "Glam yourself"),
            new CategoryEntity("Fashion", "Dress to impress"),
            new CategoryEntity("Gossip", "The talk of the town"),
            new CategoryEntity("Education", "Time to learn"),
            new CategoryEntity("Pets", "Awww"),
            new CategoryEntity("Jewellery", "Bling"),
            new CategoryEntity("Cars", "Speeeed"),
            new CategoryEntity("AI", "The end is here"),
            new CategoryEntity("Politics", "Tax the rich")
        );

        int seeded = 0;
        for (CategoryEntity c : categories) {
            if (categoryService.findCategoryByName(c.getName()).isEmpty()) {
                categoryService.saveCategory(c.getName(), c.getDescription(), null);
                seeded++;
            }
        }
        System.out.println("Seeded " + seeded + " categories into the database.");
    }
} 