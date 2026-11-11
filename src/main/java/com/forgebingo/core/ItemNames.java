package com.forgebingo.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Item name -> canonical item id, for chat lines that only carry a name (the collection log). Built from the
 * server's item catalogue ({@code GET /v1/catalogue/items}), the item ids the current board's tasks ask for and
 * the static {@link PetTable}. Never relies on {@code ItemManager.search}, which only knows tradeable items.
 * Thread-safe: the index is replaced as a whole.
 */
public final class ItemNames
{
    private volatile Map<String, Integer> catalogue = new HashMap<>();
    private volatile Map<String, Integer> board = new HashMap<>();

    /** Lower-case, typographic apostrophes and non-breaking spaces folded, inner whitespace collapsed. */
    public static String normalize(String name)
    {
        if (name == null)
        {
            return "";
        }
        return name.replace('’', '\'').replace('‘', '\'').replace(' ', ' ')
            .trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Replace the catalogue part (id/name pairs from the server). For duplicate names the lowest id wins. */
    public void setCatalogue(Collection<ApiModels.CatalogueItem> items)
    {
        catalogue = index(items);
        catalogueNames = names(items);
    }

    /** Replace the board part: names of the item ids the board's tasks ask for (from the item definitions). */
    public void setBoard(Collection<ApiModels.CatalogueItem> items)
    {
        board = index(items);
        boardNames = names(items);
    }

    private volatile Map<Integer, String> catalogueNames = new HashMap<>();
    private volatile Map<Integer, String> boardNames = new HashMap<>();

    /** The item's name (board items first, then the catalogue), or "Item &lt;id&gt;" when unknown. */
    public String name(int itemId)
    {
        String n = boardNames.get(itemId);
        if (n == null) n = catalogueNames.get(itemId);
        return n == null ? "Item " + itemId : n;
    }

    private static Map<Integer, String> names(Collection<ApiModels.CatalogueItem> items)
    {
        Map<Integer, String> m = new HashMap<>();
        if (items == null) return m;
        for (ApiModels.CatalogueItem i : items)
        {
            if (i != null && i.name != null && i.id >= 0) m.putIfAbsent(i.id, i.name);
        }
        return m;
    }

    public int size()
    {
        return catalogue.size() + board.size();
    }

    private static Map<String, Integer> index(Collection<ApiModels.CatalogueItem> items)
    {
        Map<String, Integer> m = new HashMap<>();
        if (items == null)
        {
            return m;
        }
        for (ApiModels.CatalogueItem i : items)
        {
            if (i == null || i.name == null || i.id < 0)
            {
                continue;
            }
            String key = normalize(i.name);
            if (key.isEmpty())
            {
                continue;
            }
            m.merge(key, i.id, Math::min);
        }
        return m;
    }

    /** Pets first (static, always right), then the board's own items, then the server catalogue. */
    public Optional<Integer> resolve(String name)
    {
        String key = normalize(name);
        if (key.isEmpty())
        {
            return Optional.empty();
        }
        PetTable.Pet pet = PetTable.byName(key);
        if (pet != null)
        {
            return Optional.of(pet.primaryItemId());
        }
        Integer id = board.get(key);
        if (id == null)
        {
            id = catalogue.get(key);
        }
        return Optional.ofNullable(id);
    }
}
