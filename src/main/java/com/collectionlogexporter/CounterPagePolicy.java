package com.collectionlogexporter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class CounterPagePolicy
{
	private static final Set<String> MINIGAME_COUNTER_PAGES = names(
		"Barbarian Assault",
		"Giants' Foundry",
		"Mastering Mixology",
		"Last Man Standing",
		"Gnome Restaurant",
		"Guardians of the Rift",
		"Soul Wars",
		"Hallowed Sepulchre");

	private static final Set<String> OTHER_COUNTER_PAGES = names(
		"Tormented Demons",
		"Glough's Experiments",
		"Revenants",
		"Hunter Guild");

	private CounterPagePolicy()
	{
	}

	static boolean canHaveCounter(PageDefinition definition)
	{
		switch (definition.getCategory())
		{
			case "Bosses":
			case "Raids":
			case "Clues":
				return true;
			case "Minigames":
				return MINIGAME_COUNTER_PAGES.contains(normalize(definition.getName()));
			case "Other":
				return OTHER_COUNTER_PAGES.contains(normalize(definition.getName()));
			default:
				return false;
		}
	}

	private static Set<String> names(String... values)
	{
		Set<String> names = new HashSet<>();
		Arrays.stream(values).map(CounterPagePolicy::normalize).forEach(names::add);
		return names;
	}

	private static String normalize(String value)
	{
		return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
	}
}
