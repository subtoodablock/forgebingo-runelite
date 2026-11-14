package com.forgebingo.core;

/**
 * A little nod to every skill, for the Skilling theme: something small that pops up on top of a card, grows, sits
 * there a while and goes again (a tree, an ore rock, an anvil, a fishing spot, ...). Our own pixel art (SkillProps).
 */
public enum SkillProp
{
    ATTACK, STRENGTH, DEFENCE, RANGED, PRAYER, MAGIC, RUNECRAFT, CONSTRUCTION, HITPOINTS, AGILITY, HERBLORE, THIEVING,
    CRAFTING, FLETCHING, SLAYER, HUNTER, MINING, SMITHING, FISHING, COOKING, FIREMAKING, WOODCUTTING, FARMING, SAILING;

    /** how many growth stages a prop has: 0 just appearing, 1 half grown, 2 full */
    public static final int STAGES = 3;
}
