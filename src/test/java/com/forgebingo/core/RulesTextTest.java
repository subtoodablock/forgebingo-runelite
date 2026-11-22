package com.forgebingo.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** The bingo's rules as the plugin keeps them (owner, 2026-11-22): line breaks stay, nothing else odd gets through. */
public class RulesTextTest
{
    @Test
    public void keepsLineBreaksAndDropsControlCharacters()
    {
        assertEquals("1. No buying items.\n2. Keyword in every shot.", Sanitizer.rules("1. No buying items.\r\n2. Keyword in every shot.\u0007"));
        assertEquals("a\n\nb", Sanitizer.rules("a\n\n\n\n\nb"));
        assertEquals("tab here", Sanitizer.rules("tab\there"));
    }

    @Test
    public void emptyOrMissingRulesShowNoButton()
    {
        assertNull(Sanitizer.rules(null));
        assertNull(Sanitizer.rules("  \n\n  "));
    }

    @Test
    public void longRulesAreCapped()
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6000; i++) sb.append('x');
        assertEquals(Sanitizer.MAX_RULES, Sanitizer.rules(sb.toString()).length());
    }
}
