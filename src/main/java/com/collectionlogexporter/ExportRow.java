package com.collectionlogexporter;

final class ExportRow
{
	private final String category;
	private final String page;
	private final int obtained;
	private final int total;
	private final int remaining;
	private final int itemId;
	private final String itemName;
	private final String activity;
	private final double attemptsPerHour;
	private final double dropRateAttempts;
	private final int completedAttempts;
	private final double attemptsToRate;
	private final double kcOverRate;
	private final double estimatedItemHours;
	private final boolean itemAnytime;
	private final double estimatedPageHours;
	private final boolean pageAnytime;
	private final int closestRank;
	private final String currentAttempts;
	private final String estimateMethod;

	ExportRow(
		String category,
		String page,
		int obtained,
		int total,
		int remaining,
		int itemId,
		String itemName,
		ItemEstimate estimate,
		int completedAttempts,
		double estimatedItemHours,
		boolean itemAnytime,
		double estimatedPageHours,
		boolean pageAnytime,
		int closestRank,
		String currentAttempts)
	{
		this.category = category;
		this.page = page;
		this.obtained = obtained;
		this.total = total;
		this.remaining = remaining;
		this.itemId = itemId;
		this.itemName = itemName;
		this.activity = estimate.getActivity();
		this.attemptsPerHour = estimate.getAttemptsPerHour();
		this.dropRateAttempts = estimate.getExpectedAdditionalAttempts();
		this.completedAttempts = completedAttempts;
		this.attemptsToRate = estimate.effectiveAttemptsRemaining(completedAttempts);
		this.kcOverRate = estimate.kcOverRate(completedAttempts);
		this.estimatedItemHours = estimatedItemHours;
		this.itemAnytime = itemAnytime;
		this.estimatedPageHours = estimatedPageHours;
		this.pageAnytime = pageAnytime;
		this.closestRank = closestRank;
		this.currentAttempts = currentAttempts;
		this.estimateMethod = estimate.getMethod();
	}

	String getCategory() { return category; }
	String getPage() { return page; }
	int getObtained() { return obtained; }
	int getTotal() { return total; }
	int getRemaining() { return remaining; }
	int getItemId() { return itemId; }
	String getItemName() { return itemName; }
	String getActivity() { return activity; }
	double getAttemptsPerHour() { return attemptsPerHour; }
	double getExpectedAdditionalAttempts() { return dropRateAttempts; }
	int getCompletedAttempts() { return completedAttempts; }
	double getAttemptsToRate() { return attemptsToRate; }
	double getKcOverRate() { return kcOverRate; }
	double getEstimatedItemHours() { return estimatedItemHours; }
	boolean isItemAnytime() { return itemAnytime; }
	double getEstimatedPageHours() { return estimatedPageHours; }
	boolean isPageAnytime() { return pageAnytime; }
	int getClosestRank() { return closestRank; }
	String getCurrentAttempts() { return currentAttempts; }
	String getEstimateMethod() { return estimateMethod; }
}
