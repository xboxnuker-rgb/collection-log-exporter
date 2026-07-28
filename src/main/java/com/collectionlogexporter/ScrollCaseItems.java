package com.collectionlogexporter;

import net.runelite.api.gameval.ItemID;

final class ScrollCaseItems
{
	private ScrollCaseItems()
	{
	}

	static boolean isScrollCase(int itemId)
	{
		return counterPageName(itemId) != null;
	}

	static String counterPageName(int itemId)
	{
		switch (itemId)
		{
			case ItemID.SCROLL_CASE_BEGINNER_MINOR:
			case ItemID.SCROLL_CASE_BEGINNER_MAJOR:
				return "Beginner Treasure Trails";
			case ItemID.SCROLL_CASE_EASY_MINOR:
			case ItemID.SCROLL_CASE_EASY_MAJOR:
				return "Easy Treasure Trails";
			case ItemID.SCROLL_CASE_MEDIUM_MINOR:
			case ItemID.SCROLL_CASE_MEDIUM_MAJOR:
				return "Medium Treasure Trails";
			case ItemID.SCROLL_CASE_HARD_MINOR:
			case ItemID.SCROLL_CASE_HARD_MAJOR:
				return "Hard Treasure Trails";
			case ItemID.SCROLL_CASE_ELITE_MINOR:
			case ItemID.SCROLL_CASE_ELITE_MAJOR:
				return "Elite Treasure Trails";
			case ItemID.SCROLL_CASE_MASTER_MINOR:
			case ItemID.SCROLL_CASE_MASTER_MAJOR:
				return "Master Treasure Trails";
			case ItemID.SCROLL_CASE_MIMIC:
				return "The Mimic";
			default:
				return null;
		}
	}

	static int milestone(int itemId)
	{
		switch (itemId)
		{
			case ItemID.SCROLL_CASE_BEGINNER_MINOR:
			case ItemID.SCROLL_CASE_HARD_MINOR:
			case ItemID.SCROLL_CASE_ELITE_MINOR:
				return 50;
			case ItemID.SCROLL_CASE_BEGINNER_MAJOR:
			case ItemID.SCROLL_CASE_EASY_MINOR:
			case ItemID.SCROLL_CASE_MEDIUM_MINOR:
				return 100;
			case ItemID.SCROLL_CASE_EASY_MAJOR:
				return 200;
			case ItemID.SCROLL_CASE_MEDIUM_MAJOR:
				return 250;
			case ItemID.SCROLL_CASE_HARD_MAJOR:
			case ItemID.SCROLL_CASE_ELITE_MAJOR:
				return 150;
			case ItemID.SCROLL_CASE_MASTER_MINOR:
				return 25;
			case ItemID.SCROLL_CASE_MASTER_MAJOR:
				return 75;
			case ItemID.SCROLL_CASE_MIMIC:
				return 1;
			default:
				return -1;
		}
	}

	static String progressionGroup(int itemId)
	{
		String pageName = counterPageName(itemId);
		return pageName == null ? "" : pageName;
	}

	static ItemEstimate applyMilestone(int itemId, ItemEstimate estimate)
	{
		int milestone = milestone(itemId);
		if (milestone < 0 || !estimate.isKnown())
		{
			return estimate;
		}

		if (itemId == ItemID.SCROLL_CASE_MIMIC)
		{
			// The bundled estimate is expressed in clue completions needed to
			// encounter a Mimic. Preserve that baseline duration while exposing
			// the actual one-Mimic completion milestone to the user.
			double attemptsPerHour = 1.0 / estimate.getEstimatedHours();
			return new ItemEstimate(
				estimate.getActivity(),
				attemptsPerHour,
				1.0,
				estimate.getEstimatedHours(),
				"Mimic completion milestone");
		}

		double firstTimeOverhead = Math.max(
			0.0,
			estimate.getEstimatedHours()
				- estimate.getExpectedAdditionalAttempts() / estimate.getAttemptsPerHour());
		return new ItemEstimate(
			estimate.getActivity(),
			estimate.getAttemptsPerHour(),
			milestone,
			milestone / estimate.getAttemptsPerHour() + firstTimeOverhead,
			"Exact clue completion milestone");
	}
}
