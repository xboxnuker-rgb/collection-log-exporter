package com.collectionlogexporter;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;
import net.runelite.client.hiscore.Skill;

final class HiscorePageCounters
{
	private HiscorePageCounters()
	{
	}

	static Map<String, String> map(HiscoreResult result, List<PageDefinition> definitions)
	{
		Map<String, String> counters = new HashMap<>();
		if (result == null)
		{
			return counters;
		}

		Map<String, Integer> scores = new HashMap<>();
		for (HiscoreSkill skill : HiscoreSkill.values())
		{
			if (!isUsefulCounter(skill))
			{
				continue;
			}
			Skill value = result.getSkill(skill);
			if (value != null && value.getLevel() >= 0)
			{
				scores.put(normalize(skill.getName()), value.getLevel());
			}
		}

		for (PageDefinition definition : definitions)
		{
			String pageKey = normalize(definition.getName());
			Integer count = countForPage(pageKey, scores);
			if (count != null && count >= 0)
			{
				counters.put(pageKey, "Jagex hiscores: " + count);
			}
		}
		return counters;
	}

	private static boolean isUsefulCounter(HiscoreSkill skill)
	{
		return skill.getType() == HiscoreSkillType.BOSS
			|| skill == HiscoreSkill.CLUE_SCROLL_BEGINNER
			|| skill == HiscoreSkill.CLUE_SCROLL_EASY
			|| skill == HiscoreSkill.CLUE_SCROLL_MEDIUM
			|| skill == HiscoreSkill.CLUE_SCROLL_HARD
			|| skill == HiscoreSkill.CLUE_SCROLL_ELITE
			|| skill == HiscoreSkill.CLUE_SCROLL_MASTER
			|| skill == HiscoreSkill.RIFTS_CLOSED;
	}

	private static Integer countForPage(String page, Map<String, Integer> scores)
	{
		switch (page)
		{
			case "callisto and artio":
			case "artio and callisto":
				return sum(scores, "Callisto", "Artio");
			case "venenatis and spindel":
			case "spindel and venenatis":
				return sum(scores, "Venenatis", "Spindel");
			case "vet'ion and calvar'ion":
			case "calvar'ion and vet'ion":
				return sum(scores, "Vet'ion", "Calvar'ion");
			case "dagannoth kings":
				return sum(scores, "Dagannoth Prime", "Dagannoth Rex", "Dagannoth Supreme");
			case "the nightmare":
			case "nightmare":
				return sum(scores, "Nightmare", "Phosani's Nightmare");
			case "chambers of xeric":
				return sum(scores, "Chambers of Xeric", "Chambers of Xeric: Challenge Mode");
			case "theatre of blood":
				return sum(scores, "Theatre of Blood", "Theatre of Blood: Hard Mode");
			case "tombs of amascut":
				return sum(scores, "Tombs of Amascut", "Tombs of Amascut: Expert Mode");
			case "the gauntlet":
				return sum(scores, "The Gauntlet", "The Corrupted Gauntlet");
			case "the fight caves":
			case "fight caves":
				return scores.get(normalize("TzTok-Jad"));
			case "the inferno":
			case "inferno":
				return scores.get(normalize("TzKal-Zuk"));
			case "fortis colosseum":
				return scores.get(normalize("Sol Heredit"));
			case "guardians of the rift":
				return scores.get(normalize("Rifts closed"));
			case "barrows":
				return scores.get(normalize("Barrows Chests"));
			case "the mimic":
				return scores.get(normalize("Mimic"));
			case "royal titans":
				return scores.get(normalize("The Royal Titans"));
			case "hueycoatl":
				return scores.get(normalize("The Hueycoatl"));
			case "beginner treasure trails":
				return scores.get(normalize("Clue Scrolls (beginner)"));
			case "easy treasure trails":
				return scores.get(normalize("Clue Scrolls (easy)"));
			case "medium treasure trails":
				return scores.get(normalize("Clue Scrolls (medium)"));
			case "hard treasure trails":
				return scores.get(normalize("Clue Scrolls (hard)"));
			case "elite treasure trails":
				return scores.get(normalize("Clue Scrolls (elite)"));
			case "master treasure trails":
				return scores.get(normalize("Clue Scrolls (master)"));
			default:
				return scores.get(page);
		}
	}

	private static Integer sum(Map<String, Integer> scores, String... names)
	{
		int total = 0;
		for (String name : names)
		{
			Integer value = scores.get(normalize(name));
			if (value == null)
			{
				// An unranked component is unknown, not zero. Leave the combined
				// page for a manual in-game counter instead of under-counting it.
				return null;
			}
			total += value;
		}
		return total;
	}

	private static String normalize(String value)
	{
		return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
	}
}
