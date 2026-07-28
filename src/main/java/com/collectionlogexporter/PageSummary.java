package com.collectionlogexporter;

final class PageSummary
{
	private final String category;
	private final String page;
	private final int obtained;
	private final int total;
	private final int remaining;
	private final double estimatedHours;
	private final boolean anytime;
	private final String currentAttempts;
	private final int completedAttempts;
	private final int itemsAtOrOverRate;
	private final double maxKcOverRate;
	private int rank;

	PageSummary(
		String category,
		String page,
		int obtained,
		int total,
		int remaining,
		double estimatedHours,
		boolean anytime,
		String currentAttempts,
		int completedAttempts,
		int itemsAtOrOverRate,
		double maxKcOverRate)
	{
		this.category = category;
		this.page = page;
		this.obtained = obtained;
		this.total = total;
		this.remaining = remaining;
		this.estimatedHours = estimatedHours;
		this.anytime = anytime;
		this.currentAttempts = currentAttempts;
		this.completedAttempts = completedAttempts;
		this.itemsAtOrOverRate = itemsAtOrOverRate;
		this.maxKcOverRate = maxKcOverRate;
	}

	String getCategory() { return category; }
	String getPage() { return page; }
	int getObtained() { return obtained; }
	int getTotal() { return total; }
	int getRemaining() { return remaining; }
	double getEstimatedHours() { return estimatedHours; }
	boolean isAnytime() { return anytime; }
	String getCurrentAttempts() { return currentAttempts; }
	int getCompletedAttempts() { return completedAttempts; }
	int getItemsAtOrOverRate() { return itemsAtOrOverRate; }
	double getMaxKcOverRate() { return maxKcOverRate; }
	int getRank() { return rank; }
	void setRank(int rank) { this.rank = rank; }
}
