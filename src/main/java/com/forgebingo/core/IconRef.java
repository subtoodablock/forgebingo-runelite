package com.forgebingo.core;

import java.util.regex.Pattern;

/**
 * Guards icon loading: the plugin only loads item icons via ItemManager or the two server icon endpoints
 * ({@code /v1/icons/items/<id>.png}, {@code /v1/icons/tiles/<uuid>}). Never a URL taken from board data.
 */
public final class IconRef
{
    private static final Pattern PROXY = Pattern.compile(
        "^/v1/icons/(?:items/\\d{1,6}\\.png|tiles/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$");

    private IconRef()
    {
    }

    public static boolean isProxyPath(String path)
    {
        return path != null && path.length() < 100 && PROXY.matcher(path).matches();
    }

    public static String item(int itemId)
    {
        return "/v1/icons/items/" + itemId + ".png";
    }
}
