package library;

import data.Database;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import library.model.LibraryItem;

/** Organizes and searches the catalog by genre, author, and ISBN. */
public final class CatalogSystem {

    private CatalogSystem() {
    }

    public static List<LibraryItem> allItems() {
        return Database.getAllCatalogItems();
    }

    public static Map<String, List<LibraryItem>> organizeByGenre() {
        Map<String, List<LibraryItem>> grouped = new LinkedHashMap<>();
        for (LibraryItem item : allItems()) {
            String genre = normalize(item.getGenre(), "Unknown Genre");
            grouped.computeIfAbsent(genre, k -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    public static Map<String, List<LibraryItem>> organizeByAuthor() {
        Map<String, List<LibraryItem>> grouped = new LinkedHashMap<>();
        for (LibraryItem item : allItems()) {
            String author = normalize(item.getAuthor(), "Unknown Author");
            grouped.computeIfAbsent(author, k -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    public static LibraryItem findByIsbn(String isbn) {
        if (isbn == null || isbn.isBlank()) {
            return null;
        }
        String target = isbn.trim();
        for (LibraryItem item : allItems()) {
            if (item.getIsbn() != null && item.getIsbn().equalsIgnoreCase(target)) {
                return item;
            }
        }
        return null;
    }

    /** Searches books by title or ISBN (case-insensitive). */
    public static List<LibraryItem> searchBooks(String query) {
        List<LibraryItem> results = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return results;
        }
        String q = query.trim().toLowerCase();
        for (LibraryItem item : allItems()) {
            if (!"BOOK".equals(item.getSqlItemType())) {
                continue;
            }
            String title = item.getTitle() == null ? "" : item.getTitle().toLowerCase();
            String isbn = item.getIsbn() == null ? "" : item.getIsbn().toLowerCase();
            if (title.contains(q) || isbn.contains(q)) {
                results.add(item);
            }
        }
        return results;
    }

    private static String normalize(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }
}
