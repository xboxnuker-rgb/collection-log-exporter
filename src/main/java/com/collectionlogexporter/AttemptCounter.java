package com.collectionlogexporter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AttemptCounter
{
	private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*");

	private AttemptCounter()
	{
	}

	static int fromText(String text)
	{
		if (text == null || text.trim().isEmpty())
		{
			return -1;
		}
		Matcher matcher = NUMBER.matcher(text);
		if (!matcher.find())
		{
			return -1;
		}
		try
		{
			return Integer.parseInt(matcher.group().replace(",", ""));
		}
		catch (NumberFormatException exception)
		{
			return -1;
		}
	}
}
