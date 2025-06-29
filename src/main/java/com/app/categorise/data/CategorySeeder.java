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
            new CategoryEntity("1", "Recipes", "Curated list of delicious recipes"),
            new CategoryEntity("2", "DIY", "Do-it-yourself projects and crafts"),
            new CategoryEntity("3", "Cleaning", "Cleaning tips and hacks"),
            new CategoryEntity("4", "Sport", "Sports news and updates"),
            new CategoryEntity("5", "Coding", "Building...."),
            new CategoryEntity("6", "Tech", "Nerrrrd"),
            new CategoryEntity("7", "Movies", "All things movies"),
            new CategoryEntity("8", "TV", "All things TV"),
            new CategoryEntity("9", "Music", "All things music"),
            new CategoryEntity("10", "Dance", "Moving to the beat"),
            new CategoryEntity("11", "Comedy", "Haha funny"),
            new CategoryEntity("12", "Investing", "Knowledge to help you make smart investments"),
            new CategoryEntity("13", "Travel", "Planning your next trip"),
            new CategoryEntity("14", "Lifestyle", "General lifestyle tips"),
            new CategoryEntity("15", "Workouts", "Workout routines and tips"),
            new CategoryEntity("16", "Diets", "Eat right or not..."),
            new CategoryEntity("17", "Health", "Keeping you healthy"),
            new CategoryEntity("18", "Makeup", "Glam yourself"),
            new CategoryEntity("19", "Fashion", "Dress to impress"),
            new CategoryEntity("20", "Gossip", "The talk of the town"),
            new CategoryEntity("21", "Education", "Time to learn"),
            new CategoryEntity("22", "Pets", "Awww"),
            new CategoryEntity("23", "Jewellery", "Bling"),
            new CategoryEntity("24", "Cars", "Speeeed"),
            new CategoryEntity("25", "AI", "The end is here"),
            new CategoryEntity("26", "Politics", "Tax the rich")
        );

        int seeded = 0;
        for (CategoryEntity c : categories) {
            if (categoryService.findCategoryByName(c.getName()).isEmpty()) {
                categoryService.saveCategory(c.getId(), c.getName(), c.getDescription(), c.getCreatedBy());
                seeded++;
            }
        }
        System.out.println("Seeded " + seeded + " categories into the database.");
    }
} 