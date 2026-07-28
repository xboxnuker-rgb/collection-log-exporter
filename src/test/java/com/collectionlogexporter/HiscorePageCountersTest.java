package com.collectionlogexporter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.Skill;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class HiscorePageCountersTest
{
	@Test
	public void mapsBossGroupedClueAndMinigameCounters()
	{
		Map<HiscoreSkill, Skill> skills = new HashMap<>();
		skills.put(HiscoreSkill.ABYSSAL_SIRE, score(25));
		skills.put(HiscoreSkill.CALLISTO, score(10));
		skills.put(HiscoreSkill.ARTIO, score(20));
		skills.put(HiscoreSkill.CLUE_SCROLL_EASY, score(101));
		skills.put(HiscoreSkill.RIFTS_CLOSED, score(55));

		Map<String, String> counters = HiscorePageCounters.map(
			new HiscoreResult("Player", skills),
			Arrays.asList(
				page("Bosses", "Abyssal Sire"),
				page("Bosses", "Callisto and Artio"),
				page("Clues", "Easy Treasure Trails"),
				page("Minigames", "Guardians of the Rift"),
				page("Other", "Random Events")));

		assertEquals("Jagex hiscores: 25", counters.get("abyssal sire"));
		assertEquals("Jagex hiscores: 30", counters.get("callisto and artio"));
		assertEquals("Jagex hiscores: 101", counters.get("easy treasure trails"));
		assertEquals("Jagex hiscores: 55", counters.get("guardians of the rift"));
		assertFalse(counters.containsKey("random events"));
	}

	@Test
	public void doesNotUnderCountCombinedPageWhenAComponentIsUnranked()
	{
		Map<HiscoreSkill, Skill> skills = new HashMap<>();
		skills.put(HiscoreSkill.CALLISTO, score(10));

		Map<String, String> counters = HiscorePageCounters.map(
			new HiscoreResult("Player", skills),
			Arrays.asList(page("Bosses", "Callisto and Artio")));

		assertFalse(counters.containsKey("callisto and artio"));
	}

	private static PageDefinition page(String category, String name)
	{
		return new PageDefinition(category, name, new int[]{1});
	}

	private static Skill score(int value)
	{
		return new Skill(1, value, -1);
	}
}
