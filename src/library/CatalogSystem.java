package library;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import data.Database;
import data.InputValidation;
import library.model.LibraryItem;

//help databse to see what the user need . Liek example if user use iew books then database will who avialable books
public final class CatalogSystem {

    private CatalogSystem() {
    }

    public static List<LibraryItem> allItems() {
        return Database.getAllCatalogItems();
    }

    public static Map<String, List<LibraryItem>> organizeByGenre() {
        return groupItemsBy(LibraryItem::getGenre, "Unknown Genre");
    }

    public static Map<String, List<LibraryItem>> organizeByAuthor() {
        return groupItemsBy(LibraryItem::getAuthor, "Unknown Author");
    }

    private static Map<String, List<LibraryItem>> groupItemsBy(Function<LibraryItem, String> key, String fallback) {
        Map<String, List<LibraryItem>> grouped = new LinkedHashMap<>();
        for (LibraryItem item : allItems()) {
            String label = normalize(key.apply(item), fallback);
            grouped.computeIfAbsent(label, k -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    public static LibraryItem findByIsbn(String isbn) {
        if (isbn == null || isbn.isBlank()) {
            return null;
        }
        String target = InputValidation.normalizeIsbn(isbn);
        if (target.isEmpty()) {
            return null;
        }
        for (LibraryItem item : allItems()) {
            if (item.getIsbn() == null) {
                continue;
            }
            if (InputValidation.normalizeIsbn(item.getIsbn()).equalsIgnoreCase(target)) {
                return item;
            }
        }
        return null;
    }

    //Searches catalog items by title or ISBN (case-insensitive).
    public static List<LibraryItem> searchBooks(String query) {
        List<LibraryItem> results = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return results;
        }
        String q = query.trim().toLowerCase();
        String qIsbn = InputValidation.normalizeIsbn(query).toLowerCase();
        for (LibraryItem item : allItems()) {
            String title = item.getTitle() == null ? "" : item.getTitle().toLowerCase();
            String isbn = item.getIsbn() == null ? "" : item.getIsbn().toLowerCase();
            String isbnNorm = InputValidation.normalizeIsbn(item.getIsbn()).toLowerCase();
            boolean isbnHit = isbn.contains(q) || (!qIsbn.isEmpty() && !isbnNorm.isEmpty() && isbnNorm.contains(qIsbn));
            if (title.contains(q) || isbnHit) {
                results.add(item);
            }
        }
        return results;
    }

    private static String normalize(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }
}
