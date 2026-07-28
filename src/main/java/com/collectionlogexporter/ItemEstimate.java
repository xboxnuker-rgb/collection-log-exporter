package com.collectionlogexporter;

final class ItemEstimate
{
	static final ItemEstimate UNKNOWN = new ItemEstimate("", Double.NaN, Double.NaN, Double.NaN, "");

	private final String activity;
	private final double attemptsPerHour;
	private final double expectedAdditionalAttempts;
	private final double estimatedHours;
	private final String method;

	ItemEstimate(
		String activity,
		double attemptsPerHour,
		double expectedAdditionalAttempts,
		double estimatedHours,
		String method)
	{
		this.activity = activity;
		this.attemptsPerHour = attemptsPerHour;
		this.expectedAdditionalAttempts = expectedAdditionalAttempts;
		this.estimatedHours = estimatedHours;
		this.method = method;
	}

	String getActivity()
	{
		return activity;
	}

	double getAttemptsPerHour()
	{
		return attemptsPerHour;
	}

	double getExpectedAdditionalAttempts()
	{
		return expectedAdditionalAttempts;
	}

	double getEstimatedHours()
	{
		return estimatedHours;
	}

	String getMethod()
	{
		return method;
	}

	boolean isKnown()
	{
		return Double.isFinite(estimatedHours);
	}

	double effectiveAttemptsRemaining(int completedAttempts)
	{
		if (!isKnown())
		{
			return Double.NaN;
		}
		return completedAttempts < 0
			? expectedAdditionalAttempts
			: Math.max(0.0, expectedAdditionalAttempts - completedAttempts);
	}

	double kcOverRate(int completedAttempts)
	{
		if (!isKnown() || completedAttempts < 0)
		{
			return Double.NaN;
		}
		return Math.max(0.0, completedAttempts - expectedAdditionalAttempts);
	}

	boolean isAnytime(int completedAttempts)
	{
		return isKnown() && completedAttempts >= 0
			&& completedAttempts >= expectedAdditionalAttempts;
	}

	double effectiveHours(int completedAttempts)
	{
		if (!isKnown() || completedAttempts < 0)
		{
			return estimatedHours;
		}
		if (isAnytime(completedAttempts))
		{
			return 0.0;
		}
		double rateHours = expectedAdditionalAttempts / attemptsPerHour;
		double firstTimeOverhead = Math.max(0.0, estimatedHours - rateHours);
		return effectiveAttemptsRemaining(completedAttempts) / attemptsPerHour
			+ firstTimeOverhead;
	}
}
