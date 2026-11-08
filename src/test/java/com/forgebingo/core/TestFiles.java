package com.forgebingo.core;

import java.nio.file.Path;
import net.runelite.client.util.Filepath;

/** Test helper: a RuneLite Filepath for a file in a temp folder. */
final class TestFiles
{
    private TestFiles()
    {
    }

    static Filepath fp(Path file)
    {
        return Filepath.Unchecked.getRooted(file.getParent()).joinSegment(file.getFileName().toString());
    }
}
