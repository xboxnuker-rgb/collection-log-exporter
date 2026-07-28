package com.collectionlogexporter;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CounterPagePolicyTest
{
	@Test
	public void keepsCounterPagesAndExcludesKclessPages()
	{
		assertTrue(supported("Bosses", "Abyssal Sire"));
		assertTrue(supported("Raids", "Chambers of Xeric"));
		assertTrue(supported("Clues", "Easy Treasure Trails"));
		assertTrue(supported("Minigames", "Giants' Foundry"));
		assertTrue(supported("Other", "Revenants"));

		assertFalse(supported("Minigames", "Magic Training Arena"));
		assertFalse(supported("Other", "Shooting Stars"));
		assertFalse(supported("Other", "Random Events"));
		assertFalse(supported("Other", "All Pets"));
	}

	private static boolean supported(String category, String name)
	{
		return CounterPagePolicy.canHaveCounter(
			new PageDefinition(category, name, new int[]{1}));
	}
}
