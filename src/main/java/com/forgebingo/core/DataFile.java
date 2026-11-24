package com.forgebingo.core;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lookup tables that ship as readable text instead of as Java: /com/forgebingo/data/NAME.txt, bundled in the jar
 * (nothing is ever downloaded). Each table class (ActivityItems, PetTable) parses its own file once, strictly.
 *
 * WHEN A FILE IS MISSING OR BROKEN the table class calls {@link #failed} and carries on with an EMPTY table: an
 * error is logged, loudly, once; nothing is thrown. Reason: the tables load in a static initialiser the first time
 * the game thread asks about an item, and an exception there is an Error that RuneLite's event bus does not catch,
 * so it would break the client for the player, on every later use too. A Plugin Hub plugin must never do that. An
 * empty table is not silent either: the files are part of the jar, and ActivityItemsDataTest / PetTableDataTest
 * compare what was loaded with the old Java tables row for row, so a missing, empty or malformed file cannot get
 * past the build. Parsing is all or nothing: one bad line refuses the whole file, never half a table.
 */
final class DataFile
{
    private static final Logger log = LoggerFactory.getLogger(DataFile.class);

    private DataFile()
    {
    }

    /**
     * The file's lines, trimmed, without blank lines and "#" comments.
     *
     * @throws IllegalStateException when the file is missing, unreadable or has no lines at all
     */
    static List<String> lines(String name)
    {
        List<String> out = new ArrayList<>();
        try (InputStream in = DataFile.class.getResourceAsStream("/com/forgebingo/data/" + name + ".txt"))
        {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"))
            {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) out.add(line);
            }
        }
        catch (Exception e) // a missing file is a null stream
        {
            throw new IllegalStateException(name + ".txt cannot be read", e);
        }
        if (out.isEmpty()) throw new IllegalStateException(name + ".txt is empty");
        return out;
    }

    /** Says, once and loudly, that a table could not be loaded and is empty. */
    static void failed(String name, RuntimeException e)
    {
        log.error("ForgeBingo: data file '{}' is missing or broken; this table is EMPTY and drops that need it will not count", name, e);
    }
}
